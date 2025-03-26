package io.github.chains_project.classport.instrumentation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.chains_project.classport.commons.ClassportInfo;

public class MethodInterceptorVisitorTest {

    @Test
    public void testAddToQueueAddsContentToQueue() {
        MethodInterceptorVisitor.queue.clear();
        String content = "TestClass,TestMethod,projectId,true,id,artefact,group,version,child1,child2";
        MethodInterceptorVisitor.addToQueue(content);

        assertTrue(MethodInterceptorVisitor.queue.contains(content), "The queue should contain the added content");
    }


    @Test
    public void testInitializeCSVFileHeaderCreatesFileWithHeader(@TempDir Path tempDir) throws IOException {
        MethodInterceptorVisitor.OUTPUT_FILE = tempDir.resolve("output.csv").toString();

        ClassVisitor mockClassVisitor = Mockito.mock(ClassVisitor.class);
        ClassportInfo mockAnnotation = Mockito.mock(ClassportInfo.class);

        new MethodInterceptorVisitor(mockClassVisitor, "TestClass", mockAnnotation);

        File file = new File(MethodInterceptorVisitor.OUTPUT_FILE);
        assertTrue(file.exists(), "File should be created");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            assertEquals("Class,Method,sourceProjectId,isDirect,id,artefact,group,version,childIds", header, "The CSV header should be written correctly");
        }
        
    }

    @Test
    public void testVisitMethodReturnsMethodInterceptor() {
        ClassVisitor mockClassVisitor = Mockito.mock(ClassVisitor.class);
        ClassportInfo mockAnnotation = Mockito.mock(ClassportInfo.class);
        MethodInterceptorVisitor visitor = new MethodInterceptorVisitor(mockClassVisitor, "TestClass", mockAnnotation);

        MethodVisitor result = visitor.visitMethod(Opcodes.ACC_PUBLIC, "testMethod", "()V", null, null);

        assertTrue(result instanceof MethodInterceptor, "visitMethod should return an instance of MethodInterceptor");
    }

    @Test
    public void testMethodInterceptorVisitCodeAddsToQueue() {
        MethodVisitor mockMethodVisitor = Mockito.mock(MethodVisitor.class);
        ClassportInfo mockAnnotation = Mockito.mock(ClassportInfo.class);
        Mockito.when(mockAnnotation.sourceProjectId()).thenReturn("projectId");
        Mockito.when(mockAnnotation.isDirectDependency()).thenReturn(true);
        Mockito.when(mockAnnotation.id()).thenReturn("id");
        Mockito.when(mockAnnotation.artefact()).thenReturn("artefact");
        Mockito.when(mockAnnotation.group()).thenReturn("group");
        Mockito.when(mockAnnotation.version()).thenReturn("version");
        Mockito.when(mockAnnotation.childIds()).thenReturn(new String[]{"child1", "child2"});

        MethodInterceptor interceptor = new MethodInterceptor(mockMethodVisitor, "testMethod", "TestClass", mockAnnotation);

        interceptor.visitCode();

        Mockito.verify(mockMethodVisitor).visitLdcInsn("TestClass,testMethod,projectId,true,id,artefact,group,version,child1,child2");
        Mockito.verify(mockMethodVisitor).visitMethodInsn(Opcodes.INVOKESTATIC,
                "io/github/chains_project/classport/instrumentation/MethodInterceptorVisitor",
                "addToQueue",
                "(Ljava/lang/String;)V",
                false);
    }

    @Test
    public void testShutdownHookProcessesRemainingQueueItems(@TempDir Path tempDir) throws IOException {
        MethodInterceptorVisitor.OUTPUT_FILE = tempDir.resolve("output.csv").toString();

        File file = new File(MethodInterceptorVisitor.OUTPUT_FILE);
        file.createNewFile();
    
        MethodInterceptorVisitor.addToQueue("TestContent1");
        MethodInterceptorVisitor.addToQueue("TestContent2");

        ClassVisitor mockClassVisitor = Mockito.mock(ClassVisitor.class);
        ClassportInfo mockAnnotation = Mockito.mock(ClassportInfo.class);
        Mockito.when(mockAnnotation.sourceProjectId()).thenReturn("projectId");
        Mockito.when(mockAnnotation.isDirectDependency()).thenReturn(true);
        Mockito.when(mockAnnotation.id()).thenReturn("id");
        Mockito.when(mockAnnotation.artefact()).thenReturn("artefact");
        Mockito.when(mockAnnotation.group()).thenReturn("group");
        Mockito.when(mockAnnotation.version()).thenReturn("version");
        Mockito.when(mockAnnotation.childIds()).thenReturn(new String[]{"child1", "child2"});

        MethodInterceptorVisitor visitor = new MethodInterceptorVisitor(mockClassVisitor, "TestClass", mockAnnotation);
        visitor.writeRemainingQueueToFile();

        // Verify the file contents
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            assertTrue(reader.lines().anyMatch(line -> line.contains("TestContent1")), "The file should contain TestContent1");
            assertTrue(reader.lines().anyMatch(line -> line.contains("TestContent2")), "The file should contain TestContent2");
        }
    }
}