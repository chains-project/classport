
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.chains_project.classport.analyser.Analyser;
import io.github.chains_project.classport.commons.ClassportInfo;

class AnalyserTest {

    private final Path annotatedJarPath = Path.of("src/test/resources/annotated-classes/test-agent-app-1.0-SNAPSHOT.jar");
    private final Path notAnnotatedJarPath = Path.of("src/test/resources/not-annotated-classes/test-agent-app-1.0-SNAPSHOT.jar");

    @Test
    void testGetSBOM_withAnnotatedClasses() throws IOException {
        JarFile jar = new JarFile(annotatedJarPath.toFile());
        HashMap<String, ClassportInfo> actualSbom = Analyser.getSBOM(jar);

        assertTrue(!actualSbom.isEmpty());
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
        HashMap<String, ClassportInfo> actualSbom = Analyser.getSBOM(jar);

        assertTrue(actualSbom.isEmpty(),"The sbom should be empty if the jas file does not contain annotated classes");
        String actualMessage = errContent.toString();
        assertTrue(actualMessage.contains("[Warning]"), "The warning message should match the expected pattern");
    }

}

