package tld.domain.me;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.HashMap;

/**
 * An classport officer, ready to check the classports of any and all incoming
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
                    ClassportInfo ann = typeIfLoaded.getAnnotation(ClassportInfo.class);
                    if (ann != null)
                        sbom.put("Name", ann.name());
                } else {
                    System.out.println("Class " + name + " was reloaded");
                }

                System.out.println("Entire map:\n" + sbom);
                return null;
            }
        });
    }
}
