package tld.domain.me.classport.agent;

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

    private static void printSBOM(Map<String, ClassportInfo> sbom) {
        for (Map.Entry<String, ClassportInfo> e : sbom.entrySet()) {
            ClassportInfo meta = e.getValue();
            System.out.println(e.getKey().replace('/', '.'));
            System.out.println("\tid: " + meta.id());
            System.out.println("\tartefact: " + meta.artefact());
            System.out.println("\tgroup: " + meta.group());
            System.out.println("\tversion: " + meta.version());
            String[] dependencies = meta.childIds();
            if (dependencies != null && dependencies.length > 0) {
                System.out.println("\tdependencies:");

                for (String dep : meta.childIds())
                    System.out.println("\t\t" + dep);
            }
        }
    }

    public static void premain(String argument, Instrumentation instrumentation) {
        System.out.println("[Agent] Adding shutdown hook...");
        Thread printingHook = new Thread(() -> {
            printSBOM(sbom);
        });
        Runtime.getRuntime().addShutdownHook(printingHook);

        instrumentation.addTransformer(new ClassFileTransformer() {
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
        });
    }
}
