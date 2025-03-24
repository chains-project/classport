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
    private static final Map<String, ClassportInfo> annotationCache = new ConcurrentHashMap<>();

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Agent started");
        inst.addTransformer(new MethodTransformer());
    }

    static class MethodTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (classBeingRedefined != null) {
                return classfileBuffer;
            }
            
            return transformClass(className, classfileBuffer);
        }

        private byte[] transformClass(String className, byte[] classfileBuffer) {
            try {
                ClassportInfo annotationInfo = getAnnotationInfo(className, classfileBuffer);
                if (annotationInfo != null) {
                    return applyTransformations(classfileBuffer, className, annotationInfo);
                }
            } catch (Exception e) {
                System.err.println("Error transforming class " + className + ": " + e.getMessage());
            }
            return classfileBuffer;
        }

        private ClassportInfo getAnnotationInfo(String className, byte[] classfileBuffer) {
            return annotationCache.computeIfAbsent(className, key -> AnnotationReader.getAnnotationValues(classfileBuffer));
        }

        private byte[] applyTransformations(byte[] classfileBuffer, String className, ClassportInfo annotationInfo) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassVisitor visitor = new MethodInterceptorVisitor(writer, className, annotationInfo);
            reader.accept(visitor, 0);
            return writer.toByteArray();
        }
    }
}