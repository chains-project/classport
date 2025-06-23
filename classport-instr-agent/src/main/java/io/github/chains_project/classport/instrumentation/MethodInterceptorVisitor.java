package io.github.chains_project.classport.instrumentation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.chains_project.classport.instrumentation.granularity.ClassInvocation;
import io.github.chains_project.classport.instrumentation.granularity.Granularity;
import io.github.chains_project.classport.instrumentation.granularity.MethodInvocation;
import io.github.chains_project.classport.instrumentation.granularity.RecordingStrategy;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.chains_project.classport.commons.ClassportInfo;

public class MethodInterceptorVisitor extends ClassVisitor {
    private static RecordingStrategy recordingStrategy;
    private final String className;
    private final ClassportInfo ann; 

    public MethodInterceptorVisitor(ClassVisitor cv, String className, ClassportInfo ann, String outputFileName, Path outputDir, Granularity granularity) {
        super(Opcodes.ASM9, cv);
        this.className = className;
        this.ann = ann;

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + outputDir, e);
        }

        Path outputPath = outputDir.resolve(outputFileName);

        recordingStrategy = switch (granularity) {
            case CLASS -> new ClassInvocation(outputPath);
            case METHOD -> new MethodInvocation(outputPath);
        };
        recordingStrategy.initializeCSVHeader(outputPath);
        recordingStrategy.initializeBackgroundWriter();
        // Add a shutdown hook to process remaining items in the queue
        Runtime.getRuntime().addShutdownHook(new Thread(recordingStrategy::writeToFile));
    }
    
    public static void addToInvokeLater(String className, String methodName, String classportInfo) {
        recordingStrategy.addToInvokeLater(className, methodName, classportInfo);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodInterceptor(mv, name, className, ann);
    }
}

class MethodInterceptor extends MethodVisitor {
    private final String methodName;
    private final String className;
    private final ClassportInfo ann;

    public MethodInterceptor(MethodVisitor mv, String methodName, String className, ClassportInfo ann) {
        super(Opcodes.ASM9, mv);
        this.methodName = methodName;
        this.className = className;
        this.ann = ann;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        // Inject code to add to the queue every time the method is invoked

        String classportInfo = ann.sourceProjectId() + "," +
                ann.isDirectDependency() + "," +
                ann.id() + "," +
                ann.artefact() + "," +
                ann.group() + "," +
                ann.version() + "," +
                String.join(",", ann.childIds());

        mv.visitLdcInsn(className);
        mv.visitLdcInsn(methodName);
        mv.visitLdcInsn(classportInfo);

        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "io/github/chains_project/classport/instrumentation/MethodInterceptorVisitor",
                "addToInvokeLater",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false);
    }
}


