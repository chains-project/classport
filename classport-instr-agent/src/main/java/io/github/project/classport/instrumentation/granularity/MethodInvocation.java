package io.github.project.classport.instrumentation.granularity;

import io.github.project.classport.commons.ClassportInfo;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
	public void initializeCSVHeader(Path outputPath) {
		// Write header to the file if it doesn't exist or is empty
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
			File file = outputPath.toFile();
			if (file.length() == 0) {
				writer.write("Class,Method,group,artefact,version\n");
				writer.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
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

	@Override
	public MethodVisitor startVisitor(MethodVisitor mv, String methodName, String className, ClassportInfo ann) {
		return new MethodInterceptor(mv, methodName, className, ann);
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
			mv.visitLdcInsn(className + "," + methodName + "," + ann.group() + "," + ann.artefact() + "," + ann.version());
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"io/github/project/classport/instrumentation/MethodInterceptorVisitor",
					"addToInvokeLater",
					"(Ljava/lang/String;)V",
					false);
		}
	}
