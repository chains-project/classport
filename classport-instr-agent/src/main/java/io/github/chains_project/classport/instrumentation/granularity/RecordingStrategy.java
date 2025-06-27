package io.github.chains_project.classport.instrumentation.granularity;

import io.github.chains_project.classport.commons.ClassportInfo;
import org.objectweb.asm.MethodVisitor;

import java.nio.file.Path;

public interface RecordingStrategy {
	void initializeCSVHeader(Path outputPath);

	void addToInvokeLater(String content);

	void initializeBackgroundWriter();

	void writeToFile();

	MethodVisitor startVisitor(MethodVisitor mv, String methodName, String className, ClassportInfo ann);
}
