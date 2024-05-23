package io.github.chains_project.classport.verifier;

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
public class Verifier {
    // All class files begin with the magic bytes 0xCAFEBABE
    // TODO: Refactor the Maven plugin's JarHelper, put in into classport-commons,
    // and use this from there.
    private static final byte[] magicBytes = new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };
    private static final HashMap<String, ClassportInfo> sbom = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].endsWith(".jar")) {
            System.err.println("Expected JAR file as argument");
            System.exit(1);
        }

        try (JarFile jar = new JarFile(args[0])) {
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
            System.err.println("Unable to process JAR: " + e.getMessage());
        }

        ClassportProject proj = new ClassportProject(sbom);
        System.out.println("Dependency tree:");
        proj.writeTree(new PrintWriter(System.out));
    }
}
