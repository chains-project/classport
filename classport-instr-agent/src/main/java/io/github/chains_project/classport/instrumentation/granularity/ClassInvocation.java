package io.github.chains_project.classport.instrumentation.granularity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;

public class ClassInvocation implements RecordingStrategy {
	private final LinkedHashSet<String> set = new LinkedHashSet<>();
	private final Path outputPath;

	public ClassInvocation(Path outputPath) {
		this.outputPath = outputPath;
	}

	@Override
	public void addToInvokeLater (String content) {
		set.add(content);
	}

	@Override
	public void initializeBackgroundWriter() {
		// No background thread needed as all classes are written directly to the file
	}

	@Override
	public void writeToFile() {
		try {
			Files.write(outputPath, set);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
