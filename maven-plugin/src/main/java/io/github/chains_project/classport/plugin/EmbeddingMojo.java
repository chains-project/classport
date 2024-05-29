package io.github.chains_project.classport.plugin;

import io.github.chains_project.classport.commons.*;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Mojo(name = "embed", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.COMPILE)
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

    /*
     * TODO: Move into classport commons along with all other instances
     */
    private static final byte[] magicBytes = new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };

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
        Files.walkFileTree(dirToWalk.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try (FileInputStream in = new FileInputStream(file.toFile())) {
                    byte[] bytes = in.readAllBytes();

                    if (Arrays.equals(Arrays.copyOfRange(bytes, 0, 4), magicBytes)) {
                        MetadataAdder adder = new MetadataAdder(bytes);
                        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                            getLog().debug("Embedding metadata in detected class file: " + file);
                            out.write(adder.add(metadata));
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

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
                getLog().debug("Embedding metadata for " + artifact);
                if (artifact.getFile().isFile()) {
                    JarHelper pkgr = new JarHelper(artifact.getFile(),
                            new File(localrepoRoot + "/" + artefactPath),
                            /* overwrite target if exists? */ true);
                    pkgr.embed(meta);
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
