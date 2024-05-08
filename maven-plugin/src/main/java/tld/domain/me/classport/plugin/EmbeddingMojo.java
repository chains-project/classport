package tld.domain.me.classport.plugin;

import tld.domain.me.classport.commons.*;

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

// We don't mind too much about the default lifecycle, but the ResolutionScope has to be TEST
// so that all dependencies are resolved
@Mojo(name = "classport", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyCollection = ResolutionScope.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class EmbeddingMojo
        extends AbstractMojo {
    /**
     * Gives access to the Maven project information.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Component
    private MavenSession session;

    @Component
    private ProjectBuilder projectBuilder;

    private MavenProject buildProjectForArtefact(Artifact artefact) throws MojoExecutionException {
        try {
            return projectBuilder
                    .build(artefact, session.getProjectBuildingRequest().setProcessPlugins(false))
                    .getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException("Coud not build project for " + artefact.getId(), e);
        }
    }

    // TODO: Mirror Artifact.getId() properly
    private String getDependencyId(Dependency dep) {
        return dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<Artifact> completeArtifactSet = project.getArtifacts();
        File localrepoRoot = new File("classport-files");
        // There's no way to differentiate between "the directory already exists"
        // and "failed to create the directory" without checking for existance too
        // so just allow overwriting as that's probably what we want anyway (?)
        localrepoRoot.mkdir();
        for (Artifact a : completeArtifactSet) {
            MavenProject p = buildProjectForArtefact(a);
            String fullId = a.getId();
            try {
                ClassportInfo meta = new ClassportHelper().getInstance(
                        fullId,
                        a.getArtifactId(),
                        a.getGroupId(),
                        a.getVersion(),
                        p.getModel().getDependencies()
                                .stream()
                                .map(dep -> getDependencyId(dep))
                                .collect(Collectors.toList()).toArray(String[]::new));

                String artefactPath = getArtefactPath(a);
                JarHelper pkgr = new JarHelper(a.getFile(),
                        new File(localrepoRoot + "/" + artefactPath),
                        /* overwrite target if exists? */ true);
                getLog().info("Embedding metadata for " + fullId);
                pkgr.embed(meta);
            } catch (IOException e) {
                getLog().error(e);
            }
        }
    }

    /**
     * Get an artefact's path based on its properties.
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
                a.getArtifactId(), a.getVersion(), classifier,
                "jar" /* TODO support more extensions */);
    }
}
