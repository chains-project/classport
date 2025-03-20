package io.github.chains_project.classport.instrumentation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import io.github.chains_project.classport.commons.AnnotationReader;
import io.github.chains_project.classport.commons.ClassportInfo;

public class Agent {
    //private static final Map<String, ClassportInfo> annotationCache = new HashMap<>();
    private static final Map<String, ClassportInfo> annotationCache = new ConcurrentHashMap<>();


    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Hello from the agent!");
        inst.addTransformer(new MethodTransformer());
    }

    static class MethodTransformer implements ClassFileTransformer {
        //private final Set<String> visitedClasses = new HashSet<>();
        // private static final Set<String> transformedClasses = new ConcurrentSkipListSet<>();
        // private static final Map<String, ClassportInfo> annotationCache = new HashMap<>();


        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (classBeingRedefined != null) {
                return classfileBuffer;
            }
            
            try {
                ClassportInfo ann = annotationCache.computeIfAbsent(className, key -> AnnotationReader.getAnnotationValues(classfileBuffer)); //AnnotationReader.getAnnotationValues(classfileBuffer); 
                if (ann != null) {
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); // Try COMPUTE_MAXS instead of COMPUTE_FRAMES
                    ClassReader reader = new ClassReader(classfileBuffer);
                    ClassVisitor visitor = new MethodInterceptorVisitor(writer, className, ann);
                    reader.accept(visitor, ClassReader.EXPAND_FRAMES);

                    return writer.toByteArray();
                } else {
                    return classfileBuffer; // Return original byte array if annotation is not found
                }
            } catch (Throwable e) {
                System.err.println("[Classport] Unable to process annotation for class " + className + ": " + e);
                return classfileBuffer; // Return original byte array in case of error
            }


            // if (classBeingRedefined != null || transformedClasses.contains(className)) {
            //     System.out.println("[Classport] Skipping already loaded class: " + className);
            //     return classfileBuffer;
            // }
            
        
            // try {
            //     ClassportInfo ann =  AnnotationReader.getAnnotationValues(classfileBuffer);
            //     if (ann != null) {
            //         ClassReader cr = new ClassReader(classfileBuffer);
            //         ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            //         ClassVisitor cv = new MethodInterceptorVisitor(cw, className);
            //         cr.accept(cv, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            //         transformedClasses.add(className);

            //         return cw.toByteArray();


            //     } else {
            //         //System.out.println("[Classport] Skipping class: " + className);
            //     }
            // } catch (Exception e) {
            //     e.printStackTrace();
            //     return classfileBuffer; // Fail gracefully instead of crashing
            // }
            // return classfileBuffer;
        }
    }
}