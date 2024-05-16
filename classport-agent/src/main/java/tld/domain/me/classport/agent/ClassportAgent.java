package tld.domain.me.classport.agent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import tld.domain.me.classport.commons.ClassportInfo;

/**
 * A classport agent, ready to check the classports of any and all incoming
 * classes.
 */
public class ClassportAgent {
    private static final HashMap<String, ClassportInfo> sbom = new HashMap<>();
    private static final ArrayList<String> noAnnotations = new ArrayList<>();

    private static void writeSBOM(Map<String, ClassportInfo> sbom, File outputFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (Map.Entry<String, ClassportInfo> e : sbom.entrySet()) {
                ClassportInfo meta = e.getValue();
                writer.write(e.getKey().replace('/', '.'));
                writer.newLine();
                writer.write("\tid: " + meta.id());
                writer.newLine();
                writer.write("\tartefact: " + meta.artefact());
                writer.newLine();
                writer.write("\tgroup: " + meta.group());
                writer.newLine();
                writer.write("\tversion: " + meta.version());
                writer.newLine();
                String[] dependencies = meta.childIds();
                if (dependencies != null && dependencies.length > 0) {
                    writer.write("\tdependencies:");
                    writer.newLine();

                    for (String dep : meta.childIds()) {
                        writer.write("\t\t" + dep);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
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
                        sbom.put(name, ann);
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

            writeSBOM(sbom, new File("classport-sbom"));
        });
        Runtime.getRuntime().addShutdownHook(printingHook);

        // Start instrumenting loaded classes
        instrumentation.addTransformer(transformer);
    }
}
