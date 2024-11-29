import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.chains_project.classport.agent.ClassportAgent;
import io.github.chains_project.classport.commons.AnnotationReader;
import io.github.chains_project.classport.commons.ClassportInfo;

public class ClassportAgentTest {

    private static final Path CLASSPORT_TREE_PATH = Path.of("src/test/resources/classport-deps/classport-deps-tree");
    private static final Path CLASSPORT_LIST_PATH = Path.of("src/test/resources/classport-deps/classport-deps-list");
    private static final Path ANNOTATED_CLASS_PATH = Path.of("src/test/resources/annotated-classes/StringUtils.class");
    private static final Path NOT_ANNOTATED_CLASS_PATH = Path.of("src/test/resources/not-annotated-classes/StringUtils.class");

    @Test
    void shouldGenerateDependencyListAndTreeFiles() throws Exception {
        HashMap<String, ClassportInfo> sbom = mockSBOM();

        ClassportAgent.writeSBOM(sbom);

        File actualTreeFile = new File(System.getProperty("user.dir"), "classport-deps-tree");
        File actualListFile = new File(System.getProperty("user.dir"), "classport-deps-list");
        
        assertAll(
            () -> assertTrue(actualTreeFile.exists(), "Tree output file should be created"),
            () -> assertTrue(actualListFile.exists(), "List output file should be created"),
            () -> assertEquals(
                Files.readString(CLASSPORT_TREE_PATH),
                Files.readString(actualTreeFile.toPath()), 
                "Tree files should be identical"),
            () -> assertEquals(                    
                Files.readString(CLASSPORT_LIST_PATH),
                Files.readString(actualListFile.toPath()), 
                "List files should be identical")
        );

        actualTreeFile.delete();
        actualListFile.delete();
    }

    
    @Test
    void shouldHandleEmptySBOM() throws Exception {
        HashMap<String, ClassportInfo> emptySBOM = new HashMap<>();

        ClassportAgent.writeSBOM(emptySBOM);

        File treeFile = new File(System.getProperty("user.dir"), "classport-deps-tree");
        File listFile = new File(System.getProperty("user.dir"), "classport-deps-list");

        assertTrue(treeFile.exists(), "Tree output file should be created even if SBOM is empty");
        assertTrue(listFile.exists(), "List output file should be created even if SBOM is empty");

        String treeContent = new String(Files.readAllBytes(treeFile.toPath()));
        String expectedPlaceholder = "<Unknown> (parent-only artefact)";
        
        assertTrue(treeContent.contains(expectedPlaceholder), "Tree file should contain placeholder text when SBOM is empty");
        assertTrue(listFile.length() == 0, "List file should be empty when SBOM is empty");

        // Clean up after the test
        treeFile.delete();
        listFile.delete();
    }

    @Test
    void shouldAnnotationBeCorrectlyRead() throws IOException {
        File classFile = ANNOTATED_CLASS_PATH.toFile();
        byte[] buffer = loadClassFromFile(classFile);
        ClassportInfo actualAnnotation = AnnotationReader.getAnnotationValues(buffer);

        assertAll(
            () -> assertEquals("commons-lang3", actualAnnotation.artefact()),
            () -> assertEquals("org.apache.commons", actualAnnotation.group()),
            () -> assertEquals("org.apache.commons:commons-lang3:jar:3.17.0", actualAnnotation.id()),
            () -> assertTrue(actualAnnotation.isDirectDependency()),
            () -> assertEquals("com.example:test-agent-app:jar:1.0-SNAPSHOT", actualAnnotation.sourceProjectId()),
            () -> assertEquals("3.17.0", actualAnnotation.version()),
            () -> assertEquals("org.apache.commons:commons-text:jar:1.12.0", actualAnnotation.childIds()[0])
        );        
    }

    @Test
    void shouldReturnNonAnnotatedClassesNull() throws Exception {
        byte[] nonAnnotatedClassBytes = loadClassFromFile(NOT_ANNOTATED_CLASS_PATH.toFile()); 
        ClassportInfo actualAnnotation = AnnotationReader.getAnnotationValues(nonAnnotatedClassBytes);
        assertNull(actualAnnotation);
    }


    private byte[] loadClassFromFile(File classFile) throws IOException {
    try (InputStream inputStream = new FileInputStream(classFile)) {
        return inputStream.readAllBytes();
        }
    }
    

    private HashMap<String, ClassportInfo> mockSBOM() {
        HashMap<String, ClassportInfo> sbom = new HashMap<>();
        sbom.put("com.example:test-agent-app:jar:1.0-SNAPSHOT", 
                 createClassportInfo("com.example", "1.0-SNAPSHOT", 
                                     "com.example:test-agent-app:jar:1.0-SNAPSHOT", 
                                     "test-agent-app", false, 
                                     "com.example:test-agent-app:jar:1.0-SNAPSHOT", 
                                     new String[]{"com.google.code.gson:gson:jar:2.11.0", 
                                                  "org.apache.commons:commons-lang3:jar:3.17.0"}));
        sbom.put("com.google.code.gson:gson:jar:2.11.0", 
                 createClassportInfo("com.google.code.gson", "2.11.0", 
                                     "com.google.code.gson:gson:jar:2.11.0", 
                                     "gson", true, 
                                     "com.example:test-agent-app:jar:1.0-SNAPSHOT", 
                                     new String[]{"com.google.errorprone:error_prone_annotations:jar:2.27.0"}));
        sbom.put("org.apache.commons:commons-lang3:jar:3.17.0", 
                 createClassportInfo("org.apache.commons", "3.17.0", 
                                     "org.apache.commons:commons-lang3:jar:3.17.0", 
                                     "commons-lang3", true, 
                                     "com.example:test-agent-app:jar:1.0-SNAPSHOT", 
                                     new String[]{"org.apache.commons:commons-text:jar:1.12.0"}));
        return sbom;
    }
    
    private ClassportInfo createClassportInfo(String group, String version, String id, 
                                          String artefact, boolean isDirectDependency, 
                                          String sourceProjectId, String[] childIds) {
        return new ClassportInfo() {
            @Override public String group() { return group; }
            @Override public String version() { return version; }
            @Override public String id() { return id; }
            @Override public String artefact() { return artefact; }
            @Override public boolean isDirectDependency() { return isDirectDependency; }
            @Override public String sourceProjectId() { return sourceProjectId; }
            @Override public String[] childIds() { return childIds; }
            @Override public Class<? extends Annotation> annotationType() { return ClassportInfo.class; }
        };
    }
}
