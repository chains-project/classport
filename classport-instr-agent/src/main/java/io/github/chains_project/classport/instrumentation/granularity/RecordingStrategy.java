package io.github.chains_project.classport.instrumentation.granularity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public interface RecordingStrategy {
	default void initializeCSVHeader(Path outputPath) {
		// Write header to the file if it doesn't exist or is empty
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
			File file = outputPath.toFile();
			if (file.length() == 0) {
				writer.write("Class,Method,sourceProjectId,isDirect,id,artefact,group,version,childIds\n");
				writer.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	void addToInvokeLater(String content);
	void initializeBackgroundWriter();
	void writeToFile();
}
