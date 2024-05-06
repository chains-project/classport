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

            Map<String, Boolean> processed = completeArtifactMap.keySet().stream()
                    .collect(Collectors.toMap(id -> id, val -> false));
            embedMetadata(rootNode, localrepoRoot, completeArtifactMap, processed);

            getLog().info("Embedding complete");
        } catch (DependencyCollectorBuilderException exception) {
            throw new MojoExecutionException("Unable to build project dependency graph", exception);
        }
    }

    // TODO: Refactor to embed first, recursively call later
    private void embedMetadata(DependencyNode node, File localrepoRoot, Map<String, Artifact> afs,
            Map<String, Boolean> processed) {
        node.getChildren().forEach(c -> {
            String fullId = c.getArtifact().getId();
            if (processed.get(fullId) == null) {
                String similar = processed.keySet().stream().filter(id -> id.contains(c.getArtifact().getArtifactId()))
                        .collect(Collectors.joining(", "));
                getLog().warn(
                        "Unable to locate " + c.getArtifact().getId() + " (required by " + c.getParent().getArtifact()
                                + ") in depedency list.");
                getLog().warn("This may happen when versions are specified as ranges, as the dependency graph builder "
                        + "will interpret <version>[a.b.c,d)</version> as 'version == a.b.c' rather than 'a.b.c <= version < d'. ");
                getLog().warn("If this is the case, and the version requirement is met by one of these dependencies, "
                        + "this will be used instead: " + similar);
                return;
            }

            Artifact a = afs.get(fullId);
            if (processed.get(fullId)) {
                getLog().debug("Already processed " + fullId + ", skipping");
                return;
            }

            try {
                ClassportInfo meta = new ClassportHelper().getInstance(
                        fullId,
                        a.getArtifactId(),
                        a.getGroupId(),
                        a.getVersion(),
                        c.getChildren().stream().map(dep -> dep.getArtifact().getId())
                                .collect(Collectors.toList()).toArray(String[]::new));

                String artefactPath = getArtefactPath(a);
                JarHelper pkgr = new JarHelper(a.getFile(),
                        new File(localrepoRoot + "/" + artefactPath),
                        /* overwrite target if exists? */ true);
                getLog().info("Embedding metadata for " + fullId);
                pkgr.embed(meta);

                processed.put(fullId, true);
                // Recurse on current node to get their children
                embedMetadata(c, localrepoRoot, afs, processed);
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
