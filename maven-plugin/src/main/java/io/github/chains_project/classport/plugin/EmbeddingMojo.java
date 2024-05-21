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
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Mojo(name = "classport", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.TEST)
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
     * Mirrors the fullId used as the dependency's `ClassportInfo.id()`, since
     * Dependency.getVersion() == Artifact.getBaseVersion()
     */
    private String getDependencyId(Dependency dep) {
        return dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        // There's no way to differentiate between "the directory already exists"
        // and "failed to create the directory" without checking for existance too
        // so just allow overwriting as that's probably what we want anyway (?)
        // TODO: Is there a "proper" way of producing this top-level only?
        File localrepoRoot = new File("classport-files");
        localrepoRoot.mkdir();

        for (Artifact artifact : dependencyArtifacts) {
            try {
                ClassportInfo meta = getMetadata(artifact);
                String artefactPath = getArtefactPath(artifact);
                JarHelper pkgr = new JarHelper(artifact.getFile(),
                        new File(localrepoRoot + "/" + artefactPath),
                        /* overwrite target if exists? */ true);
                getLog().info("Embedding metadata for " + artifact);
                pkgr.embed(meta);
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
        String groupId = artifact.getGroupId();
        String artefactId = artifact.getArtifactId();
        String version = artifact.getBaseVersion();
        String artefactCoordinates = groupId + ":" + artefactId + ":" + version;
        return new ClassportHelper().getInstance(
                artefactCoordinates,
                artefactId,
                groupId,
                version,
                dependencyProject.getModel().getDependencies()
                        .stream()
                        .map(transitiveDep -> getDependencyId(transitiveDep))
                        .collect(Collectors.toList()).toArray(String[]::new));
    }

    /**
     * Get an artefact's path relative to the repository root.
     *
     * TODO: Get the regular path (~/.m2/repository/...) and remove the
     * m2/repo-part instead?
     *
     * @see <a href="https://maven.apache.org/repositories/layout.html">Maven
     *      docs on repository structure</a>
     */
    private String getArtefactPath(Artifact a) {
        String classifier = a.getClassifier();
        if (classifier == null)
            classifier = "";
        else
            classifier = "-" + classifier;

        return String.format("%s/%s/%s/%s-%s%s.%s",
                a.getGroupId().replace('.', '/'),
                a.getArtifactId(),
                a.getBaseVersion(),
                a.getArtifactId(), a.getBaseVersion(), classifier,
                "jar" /* TODO support more extensions */);
    }
}
