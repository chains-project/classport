package io.github.chains_project.classport.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.chains_project.classport.commons.ClassportInfo;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EmbeddingMojoTest {

    private static final Class<?> annotationClass = ClassportInfo.class;
    private static final Path annotatedProjectClassPath =
        Paths.get("src/test/resources/test-app/target/classes/org/example/Main.class");
    private static final File projectClassFilesDir =
        new File("src/test/resources/test-app/target");
    private static final File classportFilesDir =
        new File("src/test/resources/test-app/classport-files");

    @TempDir
    Path tempDir;

    @BeforeAll
    void runPlugin() throws MavenInvocationException {
        assertEquals(0, getExitCodeRunMavenPlugin(), "Maven plugin not executed.");
    }

    @AfterAll
    void cleanUp() {
        cleanUpArtifactsDir(projectClassFilesDir.toPath());
        cleanUpArtifactsDir(classportFilesDir.toPath());
    }

    @Test
    void shouldEmbedAllProjectClasses_whenPluginRuns() throws IOException {
        assertTrue(projectClassFilesDir.exists(), "Missing target dir.");
        assertTrue(classportFilesDir.exists(), "classport-files dir not found.");

        assertTrue(areAllClassesEmbedded(projectClassFilesDir, true),
            "Not all project classes are embedded with ClassportInfo annotation");

        checkAnnotationValues(
            "org.example",
            "0.1.0",
            "org.example:hello:jar:0.1.0",
            "hello"
        );
    }

    @Test
    void shouldEmbedAllDependencyClasses_whenPluginRuns() {
        assertTrue(projectClassFilesDir.exists(), "Missing target dir.");
        assertTrue(classportFilesDir.exists(), "classport-files dir not found.");

        assertTrue(areAllClassesEmbedded(classportFilesDir, false),
            "Not all dependency classes are embedded with ClassportInfo annotation");
    }

    private boolean areAllClassesEmbedded(File baseDir, boolean areProjectClasses) {
        try {
            if (!areProjectClasses) {
                return processJarFiles(baseDir.toPath(), tempDir);
            } else {
                return processClassFilesInDirectory(baseDir.toPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void cleanUpArtifactsDir(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try {
                Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean processJarFiles(Path jarRoot, Path extractTo) throws IOException {
        return Files.walk(jarRoot)
            .filter(path -> path.toString().endsWith(".jar"))
            .allMatch(jar -> {
                try {
                    unzip(jar.toFile(), extractTo.toFile());
                    return processClassFilesInDirectory(extractTo);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            });
    }

    private boolean processClassFilesInDirectory(Path root) throws IOException {
        return Files.walk(root)
            .filter(f -> f.toString().endsWith(".class"))
            .allMatch(this::readAnnotation);
    }

    private boolean readAnnotation(Path classFile) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);
            ClassReader reader = new ClassReader(bytes);
            final boolean[] found = {false};

            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (desc.equals(Type.getDescriptor(ClassportInfo.class))) {
                        found[0] = true;
                    }
                    return super.visitAnnotation(desc, visible);
                }
            }, 0);

            return found[0];
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void checkAnnotationValues(String expectedGroup, String expectedVersion,
                                       String expectedId, String expectedArtefact) throws IOException {
        byte[] classBytes = Files.readAllBytes(annotatedProjectClassPath);
        ClassReader reader = new ClassReader(classBytes);

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (desc.equals(Type.getDescriptor(ClassportInfo.class))) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            switch (name) {
                                case "group" -> assertEquals(expectedGroup, value);
                                case "version" -> assertEquals(expectedVersion, value);
                                case "id" -> assertEquals(expectedId, value);
                                case "artefact" -> assertEquals(expectedArtefact, value);
                            }
                        }
                    };
                }
                return super.visitAnnotation(desc, visible);
            }
        }, 0);
    }

    private int getExitCodeRunMavenPlugin() throws MavenInvocationException {
        String projectDir = "src/test/resources/test-app";
        String goal = "compile io.github.chains-project:classport-maven-plugin:0.1.0-SNAPSHOT:embed";

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(projectDir, "pom.xml"));
        request.setGoals(Arrays.asList(goal.split(" ")));
        request.setBatchMode(true);

        Invoker invoker = new DefaultInvoker();
        String os = System.getProperty("os.name");
        if (os.contains("Mac")) {
            invoker.setMavenHome(new File(System.getenv("M2_HOME")));
        }

        InvocationResult result = invoker.execute(request);
        return result.getExitCode();
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }
}
