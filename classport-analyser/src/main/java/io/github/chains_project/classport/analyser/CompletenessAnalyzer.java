package io.github.chains_project.classport.analyser;

import io.github.chains_project.classport.commons.AnnotationReader;
import io.github.chains_project.classport.commons.ClassportInfo;

import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static io.github.chains_project.classport.analyser.CorrectnessAnalyser.magicBytes;

public class CompletenessAnalyzer {
	public static void main(String[] args) {
		JarFile jar;
		try {
			jar = new JarFile(args[0]);
		} catch (IOException e) {
			// Re-throw since javac doesn't know that System.exit() won't return
			throw new RuntimeException("Failed to parse " + args[0] + " as JAR file: " + e.getMessage());
		}
		Map<Boolean, Set<String>> annotationsPerClass = countFiles(jar);
		int embeddedAnnotations = annotationsPerClass.get(true).size();

		System.out.println("No embedded files: " + annotationsPerClass.get(false).size());
		for(String file : annotationsPerClass.get(false)) {
			System.out.println(file);
		}
		System.out.println("Embedded annotations: " + embeddedAnnotations);


	}

	public static Map<Boolean, Set<String>> countFiles(JarFile jar) {
		Map<Boolean, Set<String>> annotationsPerClass = new HashMap<>();
		annotationsPerClass.put(true, new HashSet<>());
		annotationsPerClass.put(false, new HashSet<>());
		try {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}

				PushbackInputStream in = new PushbackInputStream(jar.getInputStream(entry), 4);
				byte[] firstBytes = in.readNBytes(4);
				in.unread(firstBytes);

				// We only care about class files
				if (Arrays.equals(firstBytes, magicBytes)) {
					byte[] classFileBytes = in.readAllBytes();
					ClassportInfo ann = AnnotationReader.getAnnotationValues(classFileBytes);
					ClassInfo info = ClassNameExtractor.getInfo(classFileBytes);
					if (ann == null) {
						throw new RuntimeException("Class " + info.name + " does not contain ClassportInfo annotation. This should not happen.");
					} else {
						annotationsPerClass.get(true).add(info.name);
					}
				} else {
					annotationsPerClass.get(false).add(entry.getName());
				}
			}
		} catch (IOException e) {
			System.err.println("Error while printing tree: " + e.getMessage());
		}

		return annotationsPerClass;

	}


}
