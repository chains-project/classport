package io.github.chains_project.classport.instrumentation.granularity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public interface RecordingStrategy {
	void initializeCSVHeader(Path outputPath);
	void addToInvokeLater(String content);
	void initializeBackgroundWriter();
	void writeToFile();
}
