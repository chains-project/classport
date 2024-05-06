package tld.domain.me.classport.agent;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.HashMap;
import tld.domain.me.classport.commons.ClassportInfo;

/**
 * A classport agent, ready to check the classports of any and all incoming
 * classes.
 */
public class ClassportAgent {
    private static final HashMap<String, ClassportInfo> sbom = new HashMap<>();

    public static void premain(String argument, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(Module module,
                    ClassLoader loader,
                    String name,
                    Class<?> typeIfLoaded,
                    ProtectionDomain domain,
                    byte[] buffer) {
                if (typeIfLoaded == null) {
                    ClassportInfo ann = AnnotationReader.getAnnotationValues(buffer);
                    if (ann != null) {
                        sbom.put(name, ann);
                        System.out.println("Loading class " + name + " (from " + ann.group() + ":" + ann.artefact()
                                + ", version " + ann.version() + ")");
                    }
                } else {
                    System.out.println("[Agent] Re(loaded|defined) class '" +
                            typeIfLoaded.getName() + "'");
                }

                // We never transform the class, so just return null unconditionally
                return null;
            }
        });

        System.out.println("Adding shutdown hook...");

        Thread printingHook = new Thread(() -> {
            System.out.println("All loaded dependency classes: " + sbom);
        });
        Runtime.getRuntime().addShutdownHook(printingHook);
    }
}
