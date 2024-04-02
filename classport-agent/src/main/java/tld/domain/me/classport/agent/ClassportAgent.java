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
    private static HashMap<String, String> sbom;

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
                    // TODO: Use ASM to parse out the annotation values and keep an "in-memory SBOM"
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
            Class<?>[] classes = instrumentation.getAllLoadedClasses();
            if (classes.length > 0) {
                System.out.println("\033[1mLoaded classes:\033[0m");
                for (Class<?> cls : classes) {
                    ClassportInfo ann = cls.getAnnotation(ClassportInfo.class);
                    if (ann != null)
                        System.out.println("- " + ann.name() + " (" + ann.group() + ", version " + ann.version() + ")");
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(printingHook);
    }
}
