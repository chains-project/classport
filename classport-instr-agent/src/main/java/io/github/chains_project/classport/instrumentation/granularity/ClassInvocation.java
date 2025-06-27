package io.github.chains_project.classport.instrumentation.granularity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public class ClassInvocation implements RecordingStrategy {
	private final Set<String> set = new HashSet<>();
	private final Path outputPath;

	public ClassInvocation(Path outputPath) {
		this.outputPath = outputPath;
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
}
