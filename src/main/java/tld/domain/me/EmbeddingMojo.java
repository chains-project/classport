package tld.domain.me;

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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

// TODO:
//  - The LifecyclePhase might need changing (?)
//      See https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html
//  - Change the ResolutionScope?
//      See https://maven.apache.org/plugin-tools/apidocs/org/apache/maven/plugins/annotations/ResolutionScope.html
@Mojo(name = "classport", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class EmbeddingMojo
        extends AbstractMojo {
    /**
     * Gives access to the Maven project information.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<Artifact> afs = project.getArtifacts();
        var log = getLog();
        log.info("Found " + afs.size() + " artefacts.");

        File localrepoRoot = new File("classport-files");
        // There's no way to differentiate between "the directory already exists"
        // and "failed to create the directory" without checking for existance too
        // so just allow overwriting as that's probably what we want anyway (?)
        localrepoRoot.mkdir();
        for (Artifact a : afs) {
            try {
                HashMap<String, String> metadataPairs = new HashMap<>();
                // TODO More SBOM stuff here
                // - URL
                // - Parent info
                // - Checksums
                // Also TODO: Can we programmatically populate this based on the
                // annotation interface?
                metadataPairs.put("GroupId", a.getGroupId());
                metadataPairs.put("ArtifactId", a.getArtifactId());
                metadataPairs.put("Version", a.getVersion());

                String artefactPath = getArtefactPath(a);
                JarHelper pkgr = new JarHelper(a.getFile(),
                        new File(localrepoRoot + "/" + artefactPath),
                        /* overwrite target if exists */ true);
                log.info("Modifying " + a.getArtifactId());
                pkgr.embed(metadataPairs);
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        log.info("Embedding complete");
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
