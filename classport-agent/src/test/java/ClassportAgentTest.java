import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.chains_project.classport.agent.ClassportAgent;
import io.github.chains_project.classport.commons.AnnotationReader;
import io.github.chains_project.classport.commons.ClassportInfo;

public class ClassportAgentTest {

    private static final String RESOURCES_PATH = "src/test/resources/";
    private static final String CLASSPORT_TREE_PATH = RESOURCES_PATH + "classport-deps/classport-deps-tree";
    private static final String CLASSPORT_LIST_PATH = RESOURCES_PATH + "classport-deps/classport-deps-list";
    private static final String ANNOTATED_CLASS_PATH = RESOURCES_PATH + "annotated-classes/StringUtils.class";

    @Test
    void shouldGenerateDependencyListAndTreeFiles() throws Exception {
        HashMap<String, ClassportInfo> sbom = getSBOM();
        
        // Invokation of private method WriteSBOM of ClassportAgent class
        invokeWriteSBOMMethod(sbom);

        File treeFile = new File(System.getProperty("user.dir"), "classport-deps-tree");
        File listFile = new File(System.getProperty("user.dir"), "classport-deps-list");
        
        assertAll(
            () -> assertTrue(treeFile.exists(), "Tree output file should be created"),
            () -> assertTrue(listFile.exists(), "List output file should be created"),
            () -> assertTrue(compareFiles(treeFile, new File(CLASSPORT_TREE_PATH)), "Tree files should be identical."),
            () -> assertTrue(compareFiles(listFile, new File(CLASSPORT_LIST_PATH)), "List files should be identical.")
        );

        treeFile.delete();
        listFile.delete();
    }

    private void invokeWriteSBOMMethod(HashMap<String, ClassportInfo> sbom)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class<?> clazz = ClassportAgent.class;

        System.out.println("Current working directory: " + System.getProperty("user.dir"));

        Method writeSBOMMethod = clazz.getDeclaredMethod("writeSBOM", Map.class);
        writeSBOMMethod.setAccessible(true); 

        writeSBOMMethod.invoke(null, sbom); 
    }


    @Test
    void shouldAnnotationBeCorrectlyRead() throws IOException {
        File classFile = new File(ANNOTATED_CLASS_PATH);
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

    private byte[] loadClassFromFile(File classFile) throws IOException {
    try (InputStream inputStream = new FileInputStream(classFile)) {
        return inputStream.readAllBytes();
        }
    }

    private boolean compareFiles(File file1, File file2) throws IOException {
        return Arrays.equals(Files.readAllBytes(file1.toPath()), Files.readAllBytes(file2.toPath()));
    }

    

    private HashMap<String, ClassportInfo> getSBOM() {
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
