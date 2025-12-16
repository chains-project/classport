package io.github.project.classport.plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

@Mojo(name = "embed", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
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

    private void embedDirectory(Artifact a) throws IOException, MojoExecutionException {
        embedDirectory(a, a.getFile());
    }

    private void embedDirectory(Artifact a, File dirToWalk) throws IOException, MojoExecutionException {
        ClassportInfo metadata = getMetadata(a);
        if (!dirToWalk.exists()) {
            getLog().info("No classes compiled for " + project.getArtifactId() + ". Skipping.");
            return;
        }

        getLog().info("Processing compiled classes in '" + dirToWalk + "'");
        try (Stream<Path> paths = Files.walk(dirToWalk.toPath())) {
            paths.parallel()
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try (FileInputStream in = new FileInputStream(file.toFile())) {
                            byte[] header = in.readNBytes(4);
                            boolean isClassFile = header.length == 4 && Arrays.equals(header, Utility.MAGIC_BYTES);

                            if (!isClassFile) {
                                return;
                            }

                            ByteArrayOutputStream buf = new ByteArrayOutputStream((int) Files.size(file));
                            buf.write(header);
                            in.transferTo(buf);
                            byte[] original = buf.toByteArray();
                            byte[] modified = new MetadataAdder(original).add(metadata);
                            Files.write(file, modified);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to process file " + file, e);
                        }
                    });
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        // Each module gets its own classport directory
        File localrepoRoot = new File(project.getBasedir() + "/classport-files");
        localrepoRoot.mkdir();

        getLog().info("Processing project class files");
        try {
            embedDirectory(project.getArtifact(), classesDirectory);
        } catch (IOException e) {
            getLog().error("Failed to embed annotations in project class files: " + e);
        }

        getLog().info("Processing dependencies");
        for (Artifact artifact : dependencyArtifacts) {
            try {
                ClassportInfo meta = getMetadata(artifact);
                String artefactPath = getArtefactPath(artifact, true);
                File artefactFullPath = new File(localrepoRoot + "/" + artefactPath);
                getLog().debug("Embedding metadata for " + artifact);
                if (artifact.getFile().isFile()) {
                    JarHelper pkgr = new JarHelper(artifact.getFile(),
                            artefactFullPath,
                            /* overwrite target if exists? */ true);
                    pkgr.embed(meta);

                    // Also copy POMs to classport dir
                    File pomFile = new File(artifact.getFile().getAbsolutePath().replace(".jar", ".pom"));
                    File pomDestFile = new File(artefactFullPath.getAbsolutePath().replace(".jar", ".pom"));
                    if (pomFile.isFile() && !pomDestFile.exists())
                        Files.copy(Path.of(pomFile.getAbsolutePath()),
                                Path.of(pomDestFile.getAbsolutePath()));
                } else if (artifact.getFile().isDirectory()) {
                    embedDirectory(artifact);
                } else {
                    getLog().warn("Skipping " + artifact.getArtifactId()
                            + " since it does not seem to reside in either a file nor a directory");
                }
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

    /**
     * Get an artefact's path relative to the repository root.
     * If resolveSnapshotVersion is true, we get the specific snapshot
     * version instead of just "-SNAPSHOT". This may default to true
     * in the future, as this seems to be Maven's default behaviour.
     *
     * TODO: Get the regular path (~/.m2/repository/...) and remove the
     * m2/repo-part instead?
     *
     * @see <a href="https://maven.apache.org/repositories/layout.html">Maven
     *      docs on repository structure</a>
     */
    private String getArtefactPath(Artifact a, boolean resolveSnapshotVersion) {
        String classifier = a.getClassifier();
        if (classifier == null)
            classifier = "";
        else
            classifier = "-" + classifier;

        return String.format("%s/%s/%s/%s-%s%s.%s",
                a.getGroupId().replace('.', '/'),
                a.getArtifactId(),
                a.getBaseVersion(), // This seems to always be the base version
                a.getArtifactId(), (resolveSnapshotVersion ? a.getVersion() : a.getBaseVersion()), classifier,
                "jar" /* TODO support more extensions */);
    }
}
