package io.github.chains_project.classport.analyser;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
            // unimplemented
        }
    }
}
