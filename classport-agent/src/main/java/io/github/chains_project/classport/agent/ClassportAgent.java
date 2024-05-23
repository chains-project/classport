package io.github.chains_project.classport.agent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.github.chains_project.classport.commons.ClassportInfo;
import io.github.chains_project.classport.commons.ClassportProject;
import io.github.chains_project.classport.commons.AnnotationReader;

/**
 * A classport agent, ready to check the classports of any and all incoming
 * classes.
 */
public class ClassportAgent {
    private static final HashMap<String, ClassportInfo> sbom = new HashMap<>();
    private static final ArrayList<String> noAnnotations = new ArrayList<>();

    // TODO: Output in a useful format (JSON?)
    private static void writeSBOM(Map<String, ClassportInfo> sbom, File outputFile) throws IOException {
        Writer writer = outputFile == null ? new OutputStreamWriter(System.out) : new FileWriter(outputFile);

        // Model the SBOM as a hierarchical project
        ClassportProject proj = new ClassportProject(sbom);

        try (BufferedWriter bufWriter = new BufferedWriter(writer)) {
            proj.writeTree(bufWriter);
        } catch (IOException e) {
            System.err.println("Error writing SBOM: " + e.getMessage());
        }

    }

    public static void premain(String argument, Instrumentation instrumentation) {
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(Module module,
                    ClassLoader loader,
                    String name,
                    Class<?> typeIfLoaded,
                    ProtectionDomain domain,
                    byte[] buffer) {
                try {
                    ClassportInfo ann = AnnotationReader.getAnnotationValues(buffer);
                    if (ann != null)
                        sbom.put(ann.id(), ann);
                    else
                        noAnnotations.add(name);
                } catch (Throwable e) {
                    /*
                     * Catching Throwable is actually encouraged here.
                     * See https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html
                     */
                    System.err.println("[Classport] Unable to process annotation for class " + name + ": " + e);
                }

                // We never transform the class, so just return null unconditionally
                return null;
            }
        };

        // Hook into the shutdown process and print the SBOM
        Thread printingHook = new Thread(() -> {
            // Unregister the transformer, as we can't add to the map while accessing it for
            // printing anyway
            instrumentation.removeTransformer(transformer);

            try {
                writeSBOM(sbom, new File("classport-sbom"));
            } catch (IOException e) {
                System.err.println("Unable to print SBOM: " + e.getMessage());
            }
        });
        Runtime.getRuntime().addShutdownHook(printingHook);

        // Start instrumenting loaded classes
        instrumentation.addTransformer(transformer);
    }
}
