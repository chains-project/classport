package io.github.chains_project.classport.introspector;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public class Agent {
	public static void agentmain(String agentArgs, Instrumentation inst) {
		Thread.getAllStackTraces().forEach((thread, stackTrace) -> {
			System.out.println("Thread: " + thread.getName());
			for (StackTraceElement stackTraceElement : stackTrace) {
				String className = stackTraceElement.getClassName();
				System.out.println("Target JVM " + stackTraceElement.getClassLoaderName());
				System.out.println("this " + Agent.class.getClassLoader().getName());
				try {
					Class<?> c = Class.forName(className);
					c.getAnnotations();
					System.out.println(className + Arrays.toString(c.getAnnotations()));
				} catch (ClassNotFoundException e) {
					System.out.println("Class not found: " + className);
				}
			}
		});
	}
}
