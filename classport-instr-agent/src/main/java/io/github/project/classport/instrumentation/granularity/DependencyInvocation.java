package io.github.project.classport.instrumentation.granularity;

import io.github.project.classport.commons.ClassportInfo;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public class DependencyInvocation implements RecordingStrategy {
	static final Set<String> set = new HashSet<>();
	private final Path outputPath;

	public DependencyInvocation(Path outputPath) {
		this.outputPath = outputPath;
	}

	@Override
	public void initializeCSVHeader(Path outputPath) {
		// Write header to the file if it doesn't exist or is empty
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
			File file = outputPath.toFile();
			if (file.length() == 0) {
				writer.write("group,artefact,version\n");
				writer.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void addToInvokeLater(String content) {
		set.add(content);
	}

	@Override
	public void initializeBackgroundWriter() {
		// No background thread needed as all classes are written directly to the file
	}

	@Override
	public void writeToFile() {
		try {
			Files.write(outputPath, set, StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public MethodVisitor startVisitor(MethodVisitor mv, String methodName, String className, ClassportInfo ann) {
		// No specific visitor logic for class-level invocation
		return new LogDependency(mv, ann);

	}
}

class LogDependency extends MethodVisitor {
	private final ClassportInfo ann;

	public LogDependency(MethodVisitor mv, ClassportInfo ann) {
		super(Opcodes.ASM9, mv);
		this.ann = ann;
	}

	@Override
	public void visitCode() {
		super.visitCode();
		// Inject code to add to the queue every time the method is invoked
		String content = ann.group() + "," + ann.artefact() + "," + ann.version();
		if (DependencyInvocation.set.contains(content)) {
			return;
		}
		mv.visitLdcInsn(content);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC,
				"io/github/chains_project/classport/instrumentation/MethodInterceptorVisitor",
				"addToInvokeLater",
				"(Ljava/lang/String;)V",
				false);
	}
}
