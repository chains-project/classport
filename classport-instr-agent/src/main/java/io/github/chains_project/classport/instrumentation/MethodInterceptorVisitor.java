package io.github.chains_project.classport.instrumentation;

import io.github.chains_project.classport.commons.ClassportInfo;
import io.github.chains_project.classport.instrumentation.granularity.RecordingStrategy;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MethodInterceptorVisitor extends ClassVisitor {
	private static RecordingStrategy recordingStrategy;
	private final String className;
	private final ClassportInfo ann;

	public MethodInterceptorVisitor(ClassVisitor cv, String className, ClassportInfo ann, Path outputDir, RecordingStrategy recordingStrategy) {
		super(Opcodes.ASM9, cv);
		this.className = className;
		this.ann = ann;

		MethodInterceptorVisitor.recordingStrategy = recordingStrategy;
		MethodInterceptorVisitor.recordingStrategy.initializeBackgroundWriter();

		try {
			Files.createDirectories(outputDir);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create output directory: " + outputDir, e);
		}
	}

	public static void addToInvokeLater(String content) {
		recordingStrategy.addToInvokeLater(content);
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
		mv.visitLdcInsn(ann.group() + "," + ann.artefact() + "," + ann.version());
		mv.visitMethodInsn(Opcodes.INVOKESTATIC,
				"io/github/chains_project/classport/instrumentation/MethodInterceptorVisitor",
				"addToInvokeLater",
				"(Ljava/lang/String;)V",
				false);
	}
}


