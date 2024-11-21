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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import io.github.chains_project.classport.commons.ClassportInfo;


public class EmbeddingMojoTest {

    private final Class<?> annotationClass = ClassportInfo.class; 
    private final String annotatedProjectClassPath = "src/test/resources/test-app/target/classes/org/example/Main.class";

    @TempDir
    Path tempDir;

    @Test
    void shouldEmbedAllProjectClasses_whenPluginRuns() throws MavenInvocationException, IOException {

        assertEquals(0, getExitCodeRunMavenPlugin(), "Maven plugin not executed.");

        File projectClassFilesDir = new File("src/test/resources/test-app/target");
        File classportFilesDir = new File("src/test/resources/test-app/classport-files");
        assertTrue(projectClassFilesDir.exists(), "Missing target dir. Something wrong in execution of the Maven plugin.");
        assertTrue(classportFilesDir.exists(), "Classport-files dir not found. Something wrong in execution of the Maven plugin.");

        assertTrue(areAllClassesEmbedded(projectClassFilesDir, true), "Not all project classes are embedded with ClassportInfo annotation");
        checkAnnotationValues(
            "org.example",
            "0.1.0",
            "org.example:hello:jar:0.1.0",
            "hello"
        );

        cleanUpArtifactsDir(projectClassFilesDir.toPath());
        cleanUpArtifactsDir(classportFilesDir.toPath());
    }


    @Test 
    void shouldEmbedAllDependencyClasses_whenPluginRuns() throws MavenInvocationException, IOException {

        assertEquals(0, getExitCodeRunMavenPlugin(), "Maven plugin not executed.");

        File classportFilesDir = new File("src/test/resources/test-app/classport-files");
        File projectClassFilesDir = new File("src/test/resources/test-app/target");
        assertTrue(projectClassFilesDir.exists(), "Missing target dir. Something wrong in execution of the Maven plugin.");
        assertTrue(classportFilesDir.exists(), "Classport-files dir not found. Something wrong in execution of the Maven plugin.");
        
        assertTrue(areAllClassesEmbedded(classportFilesDir, false), "Not all dependency classes are embedded with ClassportInfo annotation");
        
        cleanUpArtifactsDir(projectClassFilesDir.toPath());
        cleanUpArtifactsDir(classportFilesDir.toPath());
    }


    private boolean areAllClassesEmbedded(File classportFilesDir, boolean areProjectClasses) {
        try {
            tempDir = Files.createTempDirectory("classportFilesTempDir");
            tempDir.toFile().deleteOnExit(); 
            
            if (!areProjectClasses) {
                return processJarFiles(classportFilesDir, tempDir);
            } else {
                return processClassFilesInDirectory(classportFilesDir.toPath());
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } 
    }


    private void cleanUpArtifactsDir(Path tempDir) {
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())  
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (file.isDirectory()) {
                            if (file.list().length == 0) {
                                file.delete();  
                            }
                        } else {
                            file.delete();  
                        }
                    });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private boolean processJarFiles(File classportFilesDir, Path tempDir) {
        try {
            return Files.walk(classportFilesDir.toPath())
                .filter(file -> file.toString().endsWith(".jar"))  
                .allMatch(file -> {
                    try {
                        unzip(file.toString(), tempDir.toString());
    
                        return processClassFilesInDirectory(tempDir);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        return false;
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean processClassFilesInDirectory(Path directory) {
        try {
            return Files.walk(directory)
                .filter(f -> f.toString().endsWith(".class"))  
                .allMatch(f -> readAnnotation(f.toString()));  
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private int getExitCodeRunMavenPlugin() throws MavenInvocationException, IOException{
        String projectDir = "src/test/resources/test-app"; 
        String goal = "io.github.chains-project:classport-maven-plugin:0.1.0-SNAPSHOT:embed"; 

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

    private static void unzip(String zipFile, String destFolder) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destFolder + File.separator + entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
            }
        }
    }

    private boolean readAnnotation(String classFilePath) {
        byte[] classBytes;
        try {
            classBytes = Files.readAllBytes(Paths.get(classFilePath));      
            ClassReader classReader = new ClassReader(classBytes);

            final boolean[] isAnnotationPresent = {false};

            classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (descriptor.equals(annotationClass.descriptorString())) {
                        isAnnotationPresent[0] = true;
                        return null; 
                    }
                    
                    return super.visitAnnotation(descriptor, visible);
                }
            }, 0);

            return isAnnotationPresent[0];
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void checkAnnotationValues(String expectedGroup, String expectedVersion, String expectedId, String expectedArtefac) throws IOException {
        // Load the class file
        byte[] classBytes = Files.readAllBytes(Paths.get(annotatedProjectClassPath));
        ClassReader classReader = new ClassReader(classBytes);

        // Analyze the class using a custom ClassVisitor
        classReader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals(annotationClass.descriptorString())) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("group".equals(name)) {
                                assertEquals(expectedGroup, value, "Annotation field 'value' is incorrect");
                            }
                            if ("version".equals(name)) {
                                assertEquals(expectedVersion, value, "Annotation field 'version' is incorrect");
                            }
                            if ("id".equals(name)) {
                                assertEquals(expectedId, value, "Annotation field 'id' is incorrect");
                            }
                            if ("artefact".equals(name)) {
                                assertEquals(expectedArtefac, value, "Annotation field 'artefact' is incorrect");
                            }
                        }
                    };
                }
                return super.visitAnnotation(descriptor, visible);
            }
        }, 0);
    }
    
}