package io.github.project.classport.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;

import io.github.project.classport.commons.ClassportHelper;
import io.github.project.classport.commons.ClassportInfo;
import io.github.project.classport.commons.Utility;

@Mojo(name = "embed", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class EmbeddingMojo
        extends AbstractMojo {
    /**
     * The Maven project to operate on
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    /**
     * The Maven session to operate on
     */
    @Component
    private MavenSession session;

    @Component
    private ProjectBuilder projectBuilder;

    /**
     * Directory containing the classes and resource files that should be packaged
     * into the JAR.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    /**
     * Builds a {@link MavenProject} from an {@link Artifact}
     */
    private MavenProject buildProjectForArtefact(Artifact artefact) throws MojoExecutionException {
        try {
            return projectBuilder
                    .build(artefact, session.getProjectBuildingRequest().setProcessPlugins(false))
                    .getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException("Could not build project for " + artefact.getId(), e);
        }
    }

    /*
     * Used to identify a dependency.
     *
     * Mirrors an artefact's coordinates, since
     * Dependency.getVersion() == Artifact.getBaseVersion()
     */
    private String getDependencyLongId(Dependency dep) {
        return dep.getGroupId()
                + ":" + dep.getArtifactId()
                + ":" + dep.getType()
                + (dep.getClassifier() != null ? ":" + dep.getClassifier() : "")
                + ":" + dep.getVersion();
    }

    private String getArtifactLongId(Artifact a) {
        return a.getGroupId()
                + ":" + a.getArtifactId()
                + ":" + a.getType()
                + (a.getClassifier() != null ? ":" + a.getClassifier() : "")
                + ":" + a.getVersion();
    }

    private void embedDirectory(Artifact a, File dirToWalk) throws IOException, MojoExecutionException {
        ClassportInfo metadata = getMetadata(a);
        if (!dirToWalk.exists()) {
            getLog().info("No classes compiled for " + project.getArtifactId() + ". Skipping.");
            return;
        }

        getLog().info("Processing compiled classes in '" + dirToWalk + "'");
        Files.walkFileTree(dirToWalk.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                getLog().debug("Processing file: " + file.getFileName());
                try (FileInputStream in = new FileInputStream(file.toFile())) {
                    byte[] bytes = in.readAllBytes();

                    if (Arrays.equals(Arrays.copyOfRange(bytes, 0, 4), Utility.MAGIC_BYTES)) {
                        MetadataAdder adder = new MetadataAdder(bytes);
                        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                            out.write(adder.add(metadata));
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Embed metadata directly into an artifact JAR file in-place.
     * This modifies the artifact in the local Maven repository.
     */
    private void embedArtifactInPlace(Artifact artifact)
            throws IOException, MojoExecutionException {
        File artifactFile = artifact.getFile();
        if (artifactFile == null || !artifactFile.exists()) {
            getLog().warn("Artifact file not found for " + artifact + " (file: "
                    + (artifactFile != null ? artifactFile.getAbsolutePath() : "null") + ")");
            return;
        }

        if (artifactFile.isDirectory()) {
            getLog().info(String.format("%s is a directory (most likely target/classes). We don't embed it again since it was already embedded in reactor.", artifactFile.getAbsolutePath()));
            return;
        }

        ClassportInfo meta = getMetadata(artifact);
        File tempJar = new File(artifactFile.getParent(), artifactFile.getName() + ".tmp");
        JarHelper pkgr = new JarHelper(artifactFile, tempJar, true);
        pkgr.embed(meta);

        Files.delete(artifactFile.toPath());
        Files.move(tempJar.toPath(), artifactFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        getLog().info("Embedded metadata into artifact: " + artifactFile.getAbsolutePath());
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        getLog().info("Embedding metadata into compiled classes for module: " + project.getArtifactId());
        try {
            embedDirectory(project.getArtifact(), classesDirectory);
            getLog().info("Successfully embedded metadata in compiled classes");
        } catch (IOException e) {
            getLog().error("Failed to embed annotations in project class files: " + e.getMessage(), e);
            throw new MojoExecutionException("Failed to embed annotations in project class files", e);
        }

        getLog().info("Processing dependencies");
        for (Artifact artifact : dependencyArtifacts) {
            try {
                embedArtifactInPlace(artifact);
            } catch (IOException e) {
                getLog().error("Failed to embed metadata for " + artifact + ": " + e);
            }

        }
    }

    /*
     * Get the Maven metadata of an artefact
     */
    private ClassportInfo getMetadata(Artifact artifact) throws IOException, MojoExecutionException {
        MavenProject dependencyProject = buildProjectForArtefact(artifact);
        String aId = getArtifactLongId(artifact);
        boolean isDirectDependency = this.project.getDependencies().stream().map(dep -> getDependencyLongId(dep))
                .collect(Collectors.toList()).contains(aId);

        return new ClassportHelper().getInstance(
                getArtifactLongId(project.getArtifact()), // TODO: Make into a constant
                isDirectDependency,
                aId,
                artifact.getArtifactId(),
                artifact.getGroupId(),
                artifact.getVersion(),
                dependencyProject.getModel().getDependencies()
                        .stream()
                        .filter(transitiveDep -> !transitiveDep.getScope().equals(Artifact.SCOPE_TEST))
                        .map(transitiveDep -> getDependencyLongId(transitiveDep))
                        .collect(Collectors.toList()).toArray(String[]::new));
    }

}
