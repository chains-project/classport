package tld.domain.me.classport.plugin;

import org.apache.maven.artifact.Artifact;

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

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * The dependency collector builder to use.
     */
    @Component(hint = "default")
    private DependencyCollectorBuilder dependencyCollectorBuilder;

    private DependencyNode rootNode;

    public void execute() throws MojoExecutionException, MojoFailureException {
        // For some reason unbeknownst to me, `Artifact`s retrieved from
        // `DependencyNode::getArtifact` don't seem to contain the file path, regardless
        // of what we set our dependency resolution/collection scopes to.
        //
        // There might be something I'm missing, but for now, we can just store the
        // "full" artifacts in a map. Whenever we need a dependency's artifact, we just
        // retrieve it from there (the ID should be the same so this is used as key)
        Set<Artifact> completeArtifactSet = project.getArtifacts();
        Map<String, Artifact> completeArtifactMap = completeArtifactSet.stream()
                .collect(Collectors.toMap(Artifact::getId, a -> a));
        getLog().info("Found " + completeArtifactMap.size() + " artefacts.");

        File localrepoRoot = new File("classport-files");
        // There's no way to differentiate between "the directory already exists"
        // and "failed to create the directory" without checking for existance too
        // so just allow overwriting as that's probably what we want anyway (?)
        localrepoRoot.mkdir();

        try {
            ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(
                    session.getProjectBuildingRequest());

            buildingRequest.setProject(project);
            rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, null);

            embedMetadata(rootNode, localrepoRoot, completeArtifactMap);

            getLog().info("Embedding complete");
        } catch (DependencyCollectorBuilderException exception) {
            throw new MojoExecutionException("Cannot build project dependency graph", exception);
        }
    }

    // TODO: Refactor to embed first, recursively call later
    private void embedMetadata(DependencyNode node, File localrepoRoot, Map<String, Artifact> afs) {
        node.getChildren().forEach(c -> {
            Artifact a = afs.get(c.getArtifact().getId());
            getLog().info("Processing " + a.getArtifactId());
            try {
                HashMap<String, String> metadataPairs = new HashMap<>();
                // TODO More SBOM stuff here
                // - URL
                // - Checksums
                // Also TODO: Can we programmatically populate this based on the
                // annotation interface?
                metadataPairs.put("id", a.getId());
                metadataPairs.put("group", a.getGroupId());
                metadataPairs.put("artefact", a.getArtifactId());
                metadataPairs.put("version", a.getVersion());
                metadataPairs.put("parentId", c.getParent() == null ? null : c.getParent().getArtifact().getId());

                String artefactPath = getArtefactPath(a);
                JarHelper pkgr = new JarHelper(a.getFile(),
                        new File(localrepoRoot + "/" + artefactPath),
                        /* overwrite target if exists? */ true);
                getLog().info("Embedding metadata for " + a.getArtifactId());
                pkgr.embed(metadataPairs);

                // Recurse on current node to get their children
                embedMetadata(c, localrepoRoot, afs);
            } catch (IOException e) {
                getLog().error(e);
            }

        });
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
