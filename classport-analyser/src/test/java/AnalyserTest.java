import io.github.project.classport.analyser.ClassLoadingAdder;
import io.github.project.classport.analyser.CorrectnessAnalyser;
import io.github.project.classport.commons.ClassportInfo;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrectnessAnalyserTest {

	private final Path annotatedJarPath = Path.of("src/test/resources/annotated-classes/test-agent-app-1.0-SNAPSHOT.jar");
	private final Path notAnnotatedJarPath = Path.of("src/test/resources/not-annotated-classes/test-agent-app-1.0-SNAPSHOT.jar");
	private final String addedClassName = "__classportForceClassLoading";
	private final String addedClassDescriptor = "()V";
	private final Path annotatedClassPath = Path.of("src/test/resources/annotated-classes/Main.class");
	private boolean methodPresent;
	private boolean methodInvoked;

	@Test
	void testGetSBOM_withAnnotatedClasses() throws IOException {
		JarFile jar = new JarFile(annotatedJarPath.toFile());
		HashMap<String, ClassportInfo> actualSbom = CorrectnessAnalyser.getSBOM(jar);

		assertFalse(actualSbom.isEmpty());
		assertEquals(4, actualSbom.size(), "SBOM should contain 4 annotated classes");
		assertTrue(actualSbom.containsKey("com.example:test-agent-app:jar:1.0-SNAPSHOT"), "SBOM should contain class com.example:test-agent-app:jar:1.0-SNAPSHOT");
		assertTrue(actualSbom.containsKey("com.google.errorprone:error_prone_annotations:jar:2.27.0"), "SBOM should contain class com.google.errorprone:error_prone_annotations:jar:2.27.0");
		assertTrue(actualSbom.containsKey("com.google.code.gson:gson:jar:2.11.0"), "SBOM should contain class com.google.code.gson:gson:jar:2.11.0");
		assertTrue(actualSbom.containsKey("org.apache.commons:commons-lang3:jar:3.17.0"), "SBOM should contain class org.apache.commons:commons-lang3:jar:3.17.0");
	}

	@Test
	void testGetSBOM_withNonAnnotatedClasses() throws IOException {
		ByteArrayOutputStream errContent = new ByteArrayOutputStream();
		System.setErr(new PrintStream(errContent));

		JarFile jar = new JarFile(notAnnotatedJarPath.toFile());
		HashMap<String, ClassportInfo> actualSbom = CorrectnessAnalyser.getSBOM(jar);

		assertTrue(actualSbom.isEmpty(), "The sbom should be empty if the jar file does not contain annotated classes");
		String actualMessage = errContent.toString();
		assertTrue(actualMessage.contains("[Warning]"), "The warning message should match the expected pattern");
	}

	@Test
	void testForceClassLoading_withAnnotation() throws IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		Path path = annotatedClassPath;
		byte[] originalMainClass = Files.readAllBytes(path);

		String[] classesToLoad = {"com/google/errorprone/annotations/MustBeClosed", "com/google/gson/ExclusionStrategy", "org/apache/commons/lang3/BooleanUtils"};
		byte[] modifiedClass = ClassLoadingAdder.forceClassLoading(originalMainClass, classesToLoad);

		assertNotNull(modifiedClass, "Modified class byte array should not be null");
		assertTrue(modifiedClass.length > 0, "Modified class should have content");

		ClassReader classReader = new ClassReader(modifiedClass);
		assertTrue(isAdditionalMethodPresent(classReader), "The additional method __classportForceClassLoading should be present");
		assertTrue(isAdditionalMethodInvoked(classReader), "The additional method __classportForceClassLoading should be invoked");

	}

	private boolean isAdditionalMethodPresent(ClassReader classReader) {
		methodPresent = false;
		classReader.accept(new ClassVisitor(Opcodes.ASM9) {

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (name.equals(addedClassName) && descriptor.equals(addedClassDescriptor)) {
					methodPresent = true;
				}
				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}, 0);
		return methodPresent;
	}

	private boolean isAdditionalMethodInvoked(ClassReader classReader) {
		methodInvoked = false;
		classReader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
						if (name.equals(addedClassName) && descriptor.equals(addedClassDescriptor)) {
							methodInvoked = true;
						}
						super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
					}
				};
			}

		}, 0);
		return methodInvoked;
	}
}

