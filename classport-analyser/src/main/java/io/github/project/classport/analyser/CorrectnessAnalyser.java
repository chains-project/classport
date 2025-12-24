package io.github.project.classport.analyser;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.Opcodes;

import io.github.project.classport.commons.AnnotationReader;
import io.github.project.classport.commons.ClassportInfo;
import io.github.project.classport.commons.ClassportProject;
import io.github.project.classport.commons.Utility;

/**
 *
 */
public class CorrectnessAnalyser {
    public static HashMap<String, ClassportInfo> getSBOM(JarFile jar) {
        HashMap<String, ClassportInfo> sbom = new HashMap<>();
        HashMap<String, Integer> noAnnotations = new HashMap<>();
        try {
            // TODO: There is some code duplication here. This same thing is used in the
            // io.github.project.classport.maven_plugin.JarHelper class.
            // It would be a lot better to provide an iterator in the commons package that
            // only yields class files to skip the boilerplate/repetition, or move this fn
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                PushbackInputStream in = new PushbackInputStream(jar.getInputStream(entry), 4);

                byte[] firstBytes = in.readNBytes(4);
                in.unread(firstBytes);

                // We only care about class files
                if (Arrays.equals(firstBytes, Utility.MAGIC_BYTES)) {
                    byte[] classFileBytes = in.readAllBytes();
                    ClassportInfo ann = AnnotationReader.getAnnotationValues(classFileBytes);
                    ClassInfo info = ClassNameExtractor.getInfo(classFileBytes);
                    if (ann == null) {
                        // Increment no. of classes from this package
                        String packageName;
                        if (info.name.contains("/"))
                            packageName = info.name.substring(0, info.name.lastIndexOf("/")).replace('/', '.');
                        else
                            packageName = info.name;
                        noAnnotations.put(packageName, noAnnotations.getOrDefault(packageName, 0) + 1);
                    } else {
                        sbom.put(ann.id(), ann);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error while printing tree: " + e.getMessage());
        }

        for (Entry<String, Integer> e : noAnnotations.entrySet())
            System.err.println("[Warning] " + e.getValue()
                    + " class file(s) detected without annotation for package "
                    + e.getKey());

        return sbom;
    }

    private static void printDepTree(JarFile jar) {
        HashMap<String, ClassportInfo> sbom = getSBOM(jar);
        ClassportProject proj = new ClassportProject(sbom);
        proj.writeTree(new PrintWriter(System.out));
    }

    private static void printDepList(JarFile jar) {
        HashMap<String, ClassportInfo> sbom = getSBOM(jar);
        String mainProjectId = null;
        boolean usesDependencies = false;
        
        // Get Main-Class from manifest and find its sourceProjectId
        try {
            String mainClass = jar.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClass == null) {
                throw new RuntimeException("JAR file manifest does not contain a 'Main-Class' attribute.");
            }
            
            mainClass = mainClass.replace('.', '/'); // Convert to internal name format
            
            // Find the Main-Class in the jar and get its sourceProjectId
            Enumeration<JarEntry> entries = jar.entries();
            boolean foundMainClass = false;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                PushbackInputStream in = new PushbackInputStream(jar.getInputStream(entry), 4);
                
                byte[] firstBytes = in.readNBytes(4);
                in.unread(firstBytes);
                
                // We only care about class files
                if (Arrays.equals(firstBytes, Utility.MAGIC_BYTES)) {
                    byte[] classFileBytes = in.readAllBytes();
                    ClassInfo info = ClassNameExtractor.getInfo(classFileBytes);
                    
                    if (info.name.equals(mainClass)) {
                        foundMainClass = true;
                        ClassportInfo ann = AnnotationReader.getAnnotationValues(classFileBytes);
                        if (ann == null) {
                            throw new RuntimeException("Main-Class '" + mainClass.replace('/', '.') + "' does not have a ClassportInfo annotation.");
                        }
                        mainProjectId = ann.sourceProjectId();
                        break;
                    }
                }
            }
            
            if (!foundMainClass) {
                throw new RuntimeException("Main-Class '" + mainClass.replace('/', '.') + "' not found in JAR file.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while reading manifest: " + e.getMessage(), e);
        }
        
        if (sbom.isEmpty()) {
            System.out.println("No annotations found");
            return;
        }
        
        // Print the main project ID
        System.out.println(mainProjectId);
        // Compare all classes' id() to the main project's sourceProjectId
        for (ClassportInfo c : sbom.values()) {
            if (!c.id().equals(mainProjectId)) {
                usesDependencies = true;
                System.out.println(c.id());
            }
        }

        if (!usesDependencies)
            System.out.println("No dependencies found (all class files belong to " + mainProjectId + ")");

    }

    /*
     * Generates a JAR file where the main class has been modified to force-load
     * classes from all dependencies
     *
     * @return true if successful, false if not
     */
    private static final boolean generateTestJar(JarFile jar, String outFileName, List<String> excludedClasses) {
        // Check the manifest, find the main class
        String className, mainClass;
        HashMap<String, String> classesToLoad = new HashMap<>();
        HashMap<String, Integer> noAnnotations = new HashMap<>();
        try {
            mainClass = jar.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClass == null) {
                System.err.println("JAR file manifest does not contain a 'Main-Class' attribute.");
                System.err.println("Such a JAR is not executable, so the generation will be skipped.");
                return false;
            }
            mainClass = mainClass.replace('.', '/'); // Make it compatible with internal names
            // Intercept classes and log them as <artefact name>: <class name>
            // We only need one of each so use a HashMap to override same-artefact classes
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                PushbackInputStream in = new PushbackInputStream(jar.getInputStream(entry), 4);

                byte[] firstBytes = in.readNBytes(4);
                in.unread(firstBytes);

                // We only care about class files
                if (Arrays.equals(firstBytes, Utility.MAGIC_BYTES)) {
                    byte[] classFileBytes = in.readAllBytes();
                    ClassportInfo ann = AnnotationReader.getAnnotationValues(classFileBytes);
                    // If a class is not public, we won't be able to call it.
                    ClassInfo info = ClassNameExtractor.getInfo(classFileBytes);
                    className = info.name;
                    boolean isPublic = (info.access & Opcodes.ACC_PUBLIC) != 0;

                    if (ann == null) {
                        // Increment no. of classes from this package
                        String packageName = className.substring(0, className.lastIndexOf("/")).replace('/', '.');
                        noAnnotations.put(packageName, noAnnotations.getOrDefault(packageName, 0) + 1);
                    } else if (isPublic && !className.contains("package-info")
                            && !excludedClasses.contains(className)) {
                        // Not a valid class due to "-"
                        classesToLoad.put(ann.id(), className);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Unable to process JAR file");
            return false;
        }

        for (Entry<String, Integer> e : noAnnotations.entrySet())
            System.err.println("[Warning] " + e.getValue()
                    + " class file(s) detected without annotation for package "
                    + e.getKey());

        // Go through the JAR again, stream it to the new location
        try (JarOutputStream out = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(outFileName)))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                out.putNextEntry(new JarEntry(entry.getName()));

                // Get the magic bytes
                PushbackInputStream in = new PushbackInputStream(jar.getInputStream(entry), 4);
                byte[] firstBytes = in.readNBytes(4);
                in.unread(firstBytes);

                if (Arrays.equals(firstBytes, Utility.MAGIC_BYTES)) {
                    byte[] classFileBytes = in.readAllBytes();
                    className = ClassNameExtractor.getInfo(classFileBytes).name;
                    // If this is the entry point class file, modify it
                    if (className.equals(mainClass)) {
                        out.write(ClassLoadingAdder.forceClassLoading(classFileBytes,
                                classesToLoad.values().toArray(new String[0])));
                    }
                    // Otherwise, just stream it
                    else {
                        out.write(classFileBytes);
                    }
                } else {
                    in.transferTo(out);
                }

                out.closeEntry();
            }
        } catch (IOException e) {
            System.err.println("Unable to generate modified JAR file");
        }

        return true;
    }

    public static void main(String[] args) {
        // TODO: Use picocli for a nicer interface. This is... not ideal.
        if (args.length < 2 ||
                (!args[0].equals("-generateTestJar")
                        && !args[0].equals("-printTree")
                        && !args[0].equals("-printList"))) {
            System.err.println(
                    "Usage: -<printList|printTree|generateTestJar> <jarFile> [dontUseTheseClassesForGeneration...]");
            System.exit(1);
        }

        JarFile jar;
        try {
            jar = new JarFile(args[1]);
        } catch (IOException e) {
            // Re-throw since javac doesn't know that System.exit() won't return
            throw new RuntimeException("Failed to parse " + args[1] + " as JAR file: " + e.getMessage());
        }

        if (args[0].equals("-printTree")) {
            printDepTree(jar);
        } else if (args[0].equals("-printList")) {
            printDepList(jar);
        } else if (args[0].equals("-generateTestJar")) {
            ArrayList<String> excludeClasses = new ArrayList<>();
            // The rest of the arguments are classes to ignore
            for (int i = 2; i < args.length; ++i)
                excludeClasses.add(args[i]);

            System.out.println("Generating test jar...");
            if (!excludeClasses.isEmpty())
                System.out.println("Ignored classes: " + String.join(", ", excludeClasses));

            String regenJarName = "regenerated-program.jar";
            if (generateTestJar(jar, regenJarName, excludeClasses))
                System.out.println("Generation complete. " +
                        "Regenerated JAR created at '" + regenJarName + "'");
        }
    }
}
