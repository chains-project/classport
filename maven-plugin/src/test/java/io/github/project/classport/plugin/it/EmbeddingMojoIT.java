package io.github.project.classport.plugin.it;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Disabled;

import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenOption;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;

import io.github.project.classport.analyser.CorrectnessAnalyser;
import io.github.project.classport.commons.ClassportInfo;

@Disabled("Skipping this test until we find a way to embed using a single command")
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
    }

}
