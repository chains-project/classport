package tld.domain.me;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.HashMap;

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
                if (typeIfLoaded != null) {
                    ClassportInfo classInfo = typeIfLoaded.getAnnotation(ClassportInfo.class);
                    if (classInfo != null) {
                        sbom.put(classInfo.name(), classInfo.group());
                        System.out.println("[Agent] Loaded class '" + classInfo.name() + "'.");
                    }
                }

                // TODO: Is there a difference between returning null or buffer?
                return buffer;
            }
        });
    }
}
