package io.github.project.classport.commons;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
public class AnnotationConstantPoolTest {

    private static final Path TEST_RESOURCES_DIR = Paths.get("src/test/resources");

    @Test
    void getNewEntries() throws IOException {
        // arrange
        ClassportInfo annotationInfo = new ClassportHelper().getInstance(
            "org.apache.pdfbox:pdfbox-app:bundle:3.0.4",
            true,
            "org.apache.pdfbox:jbig2-imageio:jar:3.0.4",
            "jbig2-imageio",
            "org.apache.pdfbox",
            "3.0.4",
            new String[] {}
        );

        // act
        AnnotationConstantPool annotationConstantPool = new AnnotationConstantPool(annotationInfo);
        AnnotationConstantPool.ConstantPoolData constantPoolData = annotationConstantPool.getNewEntries();

        // assert
        assertEquals(constantPoolData.entryCount(), 14);
        byte[] expectedBytes = Files.readAllLines(TEST_RESOURCES_DIR.resolve("expectedBytes.txt"))
        .stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .mapToInt(Integer::parseInt)
        .collect(ByteArrayOutputStream::new,
                 (baos, i) -> baos.write((byte) i),
                 (baos1, baos2) -> baos1.write(baos2.toByteArray(), 0, baos2.size()))
        .toByteArray();
        assertArrayEquals(constantPoolData.data(), expectedBytes);
    }

    @Test
    void injectAnnotation_noExistingAnnotations() throws IOException {
        // arrange
        ClassportInfo annotationInfo = new ClassportHelper().getInstance(
            "org.apache.pdfbox:pdfbox-app:bundle:3.0.4",
            true,
            "org.apache.pdfbox:jbig2-imageio:jar:3.0.4",
            "jbig2-imageio",
            "org.apache.pdfbox",
            "3.0.4",
            new String[] {}
        );
        byte[] originalBytes = Files.readAllBytes(TEST_RESOURCES_DIR.resolve("org.apache.pdfbox.jbig2.err.IntegerMaxValueException_original.class"));
        byte[] expectedBytes = Files.readAllBytes(TEST_RESOURCES_DIR.resolve("org.apache.pdfbox.jbig2.err.IntegerMaxValueException_annotated.class"));

        // act
        AnnotationConstantPool annotationConstantPool = new AnnotationConstantPool(annotationInfo);
        byte[] resultBytes = annotationConstantPool.injectAnnotation(originalBytes, annotationConstantPool.getNewEntries());

        // assert
        assertArrayEquals(resultBytes, expectedBytes);
    }

    @Test
    void injectAnnotation_existingAnnotations() throws IOException {
        // arrange
        ClassportInfo annotationInfo = new ClassportHelper().getInstance(
            "foo",
            false,
            "bar:jar:1.0.0",
            "bar",
            "foo",
            "1.0.0",
            new String[] {"baz:jar:2.0.0"}
        );
        byte[] originalBytes = Files.readAllBytes(TEST_RESOURCES_DIR.resolve("Main_original.class"));
        byte[] expectedBytes = Files.readAllBytes(TEST_RESOURCES_DIR.resolve("Main_annotated.class"));

        // act
        AnnotationConstantPool annotationConstantPool = new AnnotationConstantPool(annotationInfo);
        byte[] resultBytes = annotationConstantPool.injectAnnotation(originalBytes, annotationConstantPool.getNewEntries());

        // assert
        assertArrayEquals(resultBytes, expectedBytes);
    }

    @Test
    void injectAnnotation_internalClassportClass() throws IOException {
        // arrange
        ClassportInfo annotationInfo = new ClassportHelper().getInstance(
            "foo",
            true,
            "bar:jar:1.0.0",
            "bar",
            "foo",
            "1.0.0",
            new String[] {}
        );
        byte[] originalBytes = Files.readAllBytes(TEST_RESOURCES_DIR.resolve("ClassportInternal_original.class"));
        byte[] expectedBytes = Files.readAllBytes(TEST_RESOURCES_DIR.resolve("ClassportInternal_annotated.class"));

        // act
        AnnotationConstantPool annotationConstantPool = new AnnotationConstantPool(annotationInfo);
        byte[] resultBytes = annotationConstantPool.injectAnnotation(originalBytes, annotationConstantPool.getNewEntries());

        // assert
        assertArrayEquals(resultBytes, expectedBytes);
       
    }
}
