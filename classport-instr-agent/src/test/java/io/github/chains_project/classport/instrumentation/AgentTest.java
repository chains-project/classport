package io.github.chains_project.classport.instrumentation;

import java.lang.instrument.Instrumentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import io.github.chains_project.classport.commons.AnnotationReader;
import io.github.chains_project.classport.commons.ClassportInfo;

public class AgentTest {
    // Test that the premain method adds a transformer to the instrumentation and that the transformer is added only once
    @Test
    public void testPremainAddsTransformer() {
        Instrumentation mockInstrumentation = Mockito.mock(Instrumentation.class);
        Agent.premain("", mockInstrumentation);

        Mockito.verify(mockInstrumentation, Mockito.times(1)).addTransformer(Mockito.any(Agent.MethodTransformer.class));
    }

    @Test
    public void testTransformReturnsOriginalBufferForRedefinedClass() {
        Agent.MethodTransformer transformer = new Agent.MethodTransformer();
        byte[] originalBuffer = new byte[]{1, 2, 3};

        byte[] result = transformer.transform(null, "TestClass", Object.class, null, originalBuffer);

        assertArrayEquals(originalBuffer, result, "The original buffer should be returned for redefined classes");
    }

    @Test
    public void testGetAnnotationInfoCachesResults() {
        byte[] mockClassBuffer = new byte[]{1, 2, 3};
        String className = "TestClass";

        ClassportInfo mockInfo = Mockito.mock(ClassportInfo.class);
        try (MockedStatic<AnnotationReader> mockedStatic = Mockito.mockStatic(AnnotationReader.class)) {
            mockedStatic.when(() -> AnnotationReader.getAnnotationValues(mockClassBuffer)).thenReturn(mockInfo);

            Agent.MethodTransformer transformer = new Agent.MethodTransformer();
            ClassportInfo result1 = transformer.getAnnotationInfo(className, mockClassBuffer);
            ClassportInfo result2 = transformer.getAnnotationInfo(className, mockClassBuffer);

            assertSame(result1, result2, "The annotation info should be cached");
            // Verify that the annotation info is only read once
            mockedStatic.verify(() -> AnnotationReader.getAnnotationValues(mockClassBuffer), Mockito.times(1));
        }
    
    }

    private byte[] generateValidClassBytecode() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "TestClass", null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }
    
    @Test
    public void testTransformSkipsNullClassName() {
        Agent.MethodTransformer transformer = new Agent.MethodTransformer();
        byte[] originalBuffer = new byte[]{1, 2, 3};

        byte[] result = transformer.transform(null, null, null, null, originalBuffer);

        assertArrayEquals(originalBuffer, result, "The original buffer should be returned when the class name is null");
    }

    @Test
    public void testTransformClassHandlesNullAnnotationInfo() {
        Agent.MethodTransformer transformer = new Agent.MethodTransformer();
        byte[] originalBuffer = new byte[]{1, 2, 3};

        Mockito.mockStatic(AnnotationReader.class).when(() -> AnnotationReader.getAnnotationValues(originalBuffer)).thenReturn(null);

        byte[] result = transformer.transformClass("TestClass", originalBuffer);

        assertArrayEquals(originalBuffer, result, "The original buffer should be returned when annotation info is null");
    }

    @Test
    public void testTransformHandlesNullClassfileBuffer() {
        Agent.MethodTransformer transformer = new Agent.MethodTransformer();

        byte[] result = transformer.transform(null, "TestClass", null, null, null);

        assertNull(result, "The result should be null when the classfile buffer is null");
    }
    
}