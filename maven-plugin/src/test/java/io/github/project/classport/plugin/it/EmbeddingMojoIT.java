package io.github.project.classport.plugin.it;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenOption;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;

import io.github.project.classport.analyser.CorrectnessAnalyser;
import io.github.project.classport.commons.ClassportInfo;

@MavenJupiterExtension
public class EmbeddingMojoIT {
    @MavenGoal("package")
    @MavenOption("-DskipTests")
    @MavenTest
    void embed_dependency_simple_app(MavenExecutionResult result) {
        // Check that the build was successful
        assertEquals(0, result.getReturnCode());
        // Check that the embedded jar was created
        Path embeddedJar = Paths.get("target/maven-it/io/github/project/classport/plugin/it/EmbeddingMojoIT/embed_dependency_simple_app/project/target/hello-0.1.0.jar");
        JarFile jar = null;
        try {
            jar = new JarFile(embeddedJar.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Check that the SBOM size is correct
        HashMap<String, ClassportInfo> sbom = CorrectnessAnalyser.getSBOM(jar);
        assertEquals(2, sbom.size());
        // Check that the SBOM contains the expected dependencies
        Set<String> expectedIds = Set.of("org.apache.commons:commons-lang3:jar:3.17.0", "org.example:hello:jar:0.1.0");
        Set<String> actualIds = sbom.values().stream().map(ClassportInfo::id).collect(Collectors.toSet());
        assertEquals(expectedIds, actualIds);
        // Make sure that dependency in m2 repository is not embedded
        Path m2Repository = Paths.get("target/maven-it/io/github/project/classport/plugin/it/EmbeddingMojoIT/embed_dependency_simple_app/.m2/repository");
        try (JarFile m2RepositoryJar = new JarFile(m2Repository.resolve("org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar").toFile())) {
            HashMap<String, ClassportInfo> m2RepositorySbom = CorrectnessAnalyser.getSBOM(m2RepositoryJar);
            assertEquals(0, m2RepositorySbom.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to open m2 repository jar", e);
        }
    }
}
