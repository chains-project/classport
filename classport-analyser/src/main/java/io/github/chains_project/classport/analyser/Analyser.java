package io.github.chains_project.classport.analyser;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import io.github.chains_project.classport.commons.AnnotationReader;
import io.github.chains_project.classport.commons.ClassportInfo;
import io.github.chains_project.classport.commons.ClassportProject;

/**
 *
 */
public class Analyser {
    // All class files begin with the magic bytes 0xCAFEBABE
    // TODO: Refactor the Maven plugin's JarHelper, put in into classport-commons,
    // and use this from there.
    private static final byte[] magicBytes = new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };

    private static HashMap<String, ClassportInfo> getSBOM(JarFile jar) {
        HashMap<String, ClassportInfo> sbom = new HashMap<>();
        try {
            // TODO: There is some code duplication here. This same thing is used in the
            // io.github.chains_project.classport.maven_plugin.JarHelper class.
            // It would be a lot better to provide an iterator in the commons package that
            // only yields class files to skip the boilerplate/repetition, or move this fn
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                PushbackInputStream in = new PushbackInputStream(jar.getInputStream(entry), 4);

                byte[] firstBytes = in.readNBytes(4);
                in.unread(firstBytes);

                // We only care about class files
                if (Arrays.equals(firstBytes, magicBytes)) {
                    ClassportInfo ann = AnnotationReader.getAnnotationValues(in.readAllBytes());
                    if (ann == null)
                        System.err.println("[Warning] Class file detected without annotation: " + entry.getName());
                    else
                        sbom.put(ann.id(), ann);
                }
            }
        } catch (IOException e) {
            System.err.println("Error while printing tree: " + e.getMessage());
        }

        return sbom;
    }

    private static void printDepTree(JarFile jar) {
        HashMap<String, ClassportInfo> sbom = getSBOM(jar);
        ClassportProject proj = new ClassportProject(sbom);
        System.out.println("Dependency tree:");
        proj.writeTree(new PrintWriter(System.out));
    }

    private static final void generateTestJar(JarFile jar) {
        // Check the manifest, find the main class
        String className, mainClass;
        HashMap<String, String> classesToLoad = new HashMap<>();
        try {
            mainClass = jar.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClass == null) {
                System.err.println("JAR file manifest does not contain a 'Main-Class' attribute.");
                System.err.println("Such a JAR is not executable, so the generation will be skipped.");
                return;
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
                if (Arrays.equals(firstBytes, magicBytes)) {
                    byte[] classFileBytes = in.readAllBytes();
                    ClassportInfo ann = AnnotationReader.getAnnotationValues(classFileBytes);
                    className = ClassNameExtractor.getName(classFileBytes);
                    // TODO: Also read the actual class name so that we can inject it properly?
                    if (ann == null)
                        System.err.println("[Warning] Class file detected without annotation: " + entry.getName());
                    else if (!className.contains("package-info")) // Not a valid class due to "-"
                        classesToLoad.put(ann.id(), className);
                }
            }
        } catch (IOException e) {
            System.err.println("Unable to process JAR file");
            return;
        }


        // Go through the JAR again, stream it to the new location
        try (JarOutputStream out = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream("regenerated-program.jar")))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                out.putNextEntry(new JarEntry(entry.getName()));

                // Get the magic bytes
                PushbackInputStream in = new PushbackInputStream(jar.getInputStream(entry), 4);
                byte[] firstBytes = in.readNBytes(4);
                in.unread(firstBytes);

                if (Arrays.equals(firstBytes, magicBytes)) {
                    byte[] classFileBytes = in.readAllBytes();
                    className = ClassNameExtractor.getName(classFileBytes);
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
    }

    public static void main(String[] args) {
        // TODO: Use picocli for a nicer interface
        if (args.length != 2 || (!args[0].equals("-generateTestJar") && !args[0].equals("-printTree"))) {
            System.err.println("Usage: -<printTree|generateTestJar> <jarFile>");
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
        } else if (args[0].equals("-generateTestJar")) {
            generateTestJar(jar);
        }
    }
}
