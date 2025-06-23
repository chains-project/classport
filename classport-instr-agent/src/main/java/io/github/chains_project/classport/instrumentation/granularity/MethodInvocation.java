package io.github.chains_project.classport.instrumentation.granularity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MethodInvocation implements RecordingStrategy {
	private static final int QUEUE_CAPACITY = 1_000_000;
	private static final int QUEUE_THRESHOLD = 100_000;
	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
	private final Path outputPath;

	public MethodInvocation(Path outputPath) {
		this.outputPath = outputPath;
	}

	@Override
	public void addToInvokeLater(String content) {
		queue.offer(content);
	}

	@Override
	public void initializeBackgroundWriter() {
		// Initialize the queue processing thread
		Thread writerThread = new Thread(() -> {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
				while (true) {
					if (queue.size() >= QUEUE_THRESHOLD) {
						synchronized (queue) {
							while (!queue.isEmpty()) {
								writer.write(queue.poll() + "\n");
							}
							writer.flush(); // Flush after processing the batch
						}
					}
					Thread.sleep(100); // Avoid busy-waiting
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
		writerThread.setDaemon(true);
		writerThread.start();
	}

	@Override
	public void writeToFile() {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
			synchronized (queue) {
				while (!queue.isEmpty()) {
					writer.write(queue.poll() + "\n");
				}
				writer.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
