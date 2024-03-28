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
        System.out.println("Loading interface class " + ClassportInfo.class.getName() + "...");
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
                        System.out.println("[Agent] Entire map: " + sbom);
                    }
                }

                // Returns: a well-formed class file buffer (the result of the transform), or
                // null if no transform is performed
                //
                // See
                // https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/ClassFileTransformer.html#transform-java.lang.ClassLoader-java.lang.String-java.lang.Class-java.security.ProtectionDomain-byte:A-
                return null;
            }
        });
    }
}
