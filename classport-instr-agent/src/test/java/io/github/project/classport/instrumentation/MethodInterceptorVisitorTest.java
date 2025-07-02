package io.github.project.classport.instrumentation;

import io.github.project.classport.instrumentation.granularity.MethodInvocation;
import io.github.project.classport.instrumentation.granularity.RecordingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodInterceptorVisitorTest {


	@Test
	public void testInitializeCSVFileHeaderCreatesFileWithHeader(@TempDir Path tempDir) throws IOException {
		Path outputFilePath = tempDir.resolve("output.csv");

		RecordingStrategy recordingStrategy = new MethodInvocation(outputFilePath);
		recordingStrategy.initializeCSVHeader(outputFilePath);

		assertTrue(outputFilePath.toFile().exists(), "File should be created");
		assertEquals("Class,Method,group,artefact,version\n",
				Files.readString(outputFilePath), "The CSV header should be written correctly");
	}
}