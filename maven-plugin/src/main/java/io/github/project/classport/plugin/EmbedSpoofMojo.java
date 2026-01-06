package io.github.project.classport.plugin;

import java.io.File;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "embed-spoof", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class EmbedSpoofMojo
        extends AbstractMojo {
    /**
     * The Maven project to operate on
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    /**
     * Destination path (jar) inside the aggregated repository for the given
     * artifact.
     */
    private File getRepoPathForArtifact(Artifact artifact, File repoRoot) {
        String groupPath = artifact.getGroupId().replace('.', File.separatorChar);
        File baseDir = Paths.get(repoRoot.getAbsolutePath(), groupPath, artifact.getArtifactId(), artifact.getVersion()).toFile();
        String classifierPart = artifact.getClassifier() != null ? "-" + artifact.getClassifier() : "";
        String extension = artifact.getArtifactHandler().getExtension();
        return Paths.get(baseDir.getAbsolutePath(), artifact.getArtifactId() + "-" + artifact.getVersion() + classifierPart + "." + extension).toFile();
    }

    /**
     * Points artifact to the embedded JAR if it exists, without doing any embedding.
     * This is used for tests to reference the embedded dependencies created by the embed goal.
     */
    private void spoofArtifactToEmbedded(Artifact artifact, File repoRoot) {
        File destJar = getRepoPathForArtifact(artifact, repoRoot);
        
        if (destJar.exists() && destJar.isFile()) {
            // This makes Maven use the embedded JAR (same as EmbeddingMojo line 171)
            artifact.setFile(destJar);
            getLog().debug("Updated artifact reference: " + artifact.getId() + " -> " + destJar.getAbsolutePath());
        } else {
            getLog().debug("Embedded artifact not found for " + artifact.getId() + " at " + destJar.getAbsolutePath() + ", using original");
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        getLog().info("Spoofing dependencies to use embedded versions for module: " + project.getArtifactId());
        
        File classportFilesDir = new File(project.getBuild().getDirectory(), "classport-files");
        
        if (!classportFilesDir.exists()) {
            getLog().warn("Classport files directory not found at " + classportFilesDir.getAbsolutePath() + ". Embedded dependencies may not be available.");
            return;
        }

        getLog().info("Processing dependencies");
        for (Artifact artifact : dependencyArtifacts) {
            spoofArtifactToEmbedded(artifact, classportFilesDir);
        }
        
        getLog().info("Finished spoofing dependencies to embedded versions");
    }
}

