package io.github.chains_project.classport.instrumentation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import io.github.chains_project.classport.commons.AnnotationReader;
import io.github.chains_project.classport.commons.ClassportInfo;

public class Agent {
    private static final Map<String, ClassportInfo> annotationCache = new ConcurrentHashMap<>();
    private static final String TIMESTAMP = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    static String OUTPUT_FILE = "_" + TIMESTAMP + ".csv";
    static Path OUTPUT_PATH_DIR = Paths.get(System.getProperty("user.dir"), "output");


    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Agent started");
        if (agentArgs != null && !agentArgs.isEmpty()) {
            String[] args = agentArgs.split(",");
            if (args.length > 0) {
                OUTPUT_FILE = args[0].concat(OUTPUT_FILE);
            }
            if (args.length > 1) {
                OUTPUT_PATH_DIR = Paths.get(args[1]);
            } else {
                OUTPUT_PATH_DIR = Paths.get(System.getProperty("user.dir"), "output");
            }            
            System.out.println("Output file: " + OUTPUT_FILE);
            System.out.println("Output path: " + OUTPUT_PATH_DIR);
        }
        inst.addTransformer(new MethodTransformer());
    }

    static class MethodTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (classBeingRedefined != null ) {
                return classfileBuffer;
            }
            
            return transformClass(className, classfileBuffer);
        }

        public byte[] transformClass(String className, byte[] classfileBuffer) {
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

        public ClassportInfo getAnnotationInfo(String className, byte[] classfileBuffer) {
            return annotationCache.computeIfAbsent(className, key -> AnnotationReader.getAnnotationValues(classfileBuffer));
        }

        public byte[] applyTransformations(byte[] classfileBuffer, String className, ClassportInfo annotationInfo) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassVisitor visitor = new MethodInterceptorVisitor(writer, className, annotationInfo, OUTPUT_FILE, OUTPUT_PATH_DIR);
            reader.accept(visitor, 0);
            return writer.toByteArray();
        }
    }
}