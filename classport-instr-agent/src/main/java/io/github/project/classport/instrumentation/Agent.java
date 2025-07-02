package io.github.project.classport.instrumentation;

import io.github.project.classport.commons.AnnotationReader;
import io.github.project.classport.commons.ClassportInfo;
import io.github.project.classport.instrumentation.granularity.DependencyInvocation;
import io.github.project.classport.instrumentation.granularity.Granularity;
import io.github.project.classport.instrumentation.granularity.MethodInvocation;
import io.github.project.classport.instrumentation.granularity.RecordingStrategy;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Agent {
	private static final Map<String, ClassportInfo> annotationCache = new ConcurrentHashMap<>();
	private static final String TIMESTAMP = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
	static String OUTPUT_FILE = "_" + TIMESTAMP + ".csv";
	static Path OUTPUT_PATH_DIR = Paths.get(System.getProperty("user.dir"), "output");
	static List<String> nonAnnotatedClasses = new ArrayList<>();
	static Granularity granularity = Granularity.METHOD; // Default
	private static RecordingStrategy recordingStrategy;

	public static void premain(String agentArgs, Instrumentation inst) {
		System.out.println("Agent started");
		if (agentArgs != null && !agentArgs.isEmpty()) {
			String[] args = agentArgs.split(",");
			if (args.length > 0) {
				OUTPUT_FILE = args[0].concat(OUTPUT_FILE);
			}
			if (args.length > 1) {
				OUTPUT_PATH_DIR = Paths.get(args[1]);
			} else {
				OUTPUT_PATH_DIR = Paths.get(System.getProperty("user.dir"), "output");
			}
			if (args.length > 2) {
				granularity = Granularity.fromString(args[2].toUpperCase());
			}
			System.out.println("Output file: " + OUTPUT_FILE);
			System.out.println("Output path: " + OUTPUT_PATH_DIR);
			System.err.println("Not annotated classes will be saved in " + OUTPUT_FILE.replace(".csv", "_nonAnnotatedClasses.txt"));
			Path outputPath = OUTPUT_PATH_DIR.resolve(OUTPUT_FILE);

			recordingStrategy = switch (granularity) {
				case DEPENDENCY -> new DependencyInvocation(outputPath);
				case METHOD -> new MethodInvocation(outputPath);
			};
			recordingStrategy.initializeCSVHeader(outputPath);
			Runtime.getRuntime().addShutdownHook(new Thread(recordingStrategy::writeToFile));

		}
		inst.addTransformer(new MethodTransformer());
	}

	static class MethodTransformer implements ClassFileTransformer {
		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
			if (classBeingRedefined != null) {
				return classfileBuffer;
			}

			return transformClass(className, classfileBuffer);
		}

		public byte[] transformClass(String className, byte[] classfileBuffer) {
			try {
				ClassportInfo annotationInfo = getAnnotationInfo(className, classfileBuffer);
				if (annotationInfo != null) {
					return applyTransformations(classfileBuffer, className, annotationInfo);
				} else {
					nonAnnotatedClasses.add(className);
				}
			} catch (Exception e) {
				System.err.println("Error transforming class " + className + ": " + e.getMessage());
			}
			return classfileBuffer;
		}

		public ClassportInfo getAnnotationInfo(String className, byte[] classfileBuffer) {
			return annotationCache.computeIfAbsent(className, key -> AnnotationReader.getAnnotationValues(classfileBuffer));
		}

		public byte[] applyTransformations(byte[] classfileBuffer, String className, ClassportInfo annotationInfo) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			ClassReader reader = new ClassReader(classfileBuffer);
			ClassVisitor visitor = new MethodInterceptorVisitor(writer, className, annotationInfo, OUTPUT_PATH_DIR, recordingStrategy);
			reader.accept(visitor, 0);
			return writer.toByteArray();
		}
	}
}