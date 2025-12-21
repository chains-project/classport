package io.github.project.classport.plugin;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import io.github.project.classport.commons.AnnotationConstantPool;
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

    /**
     * Generate a simple key for identifying a reactor module by its GAV coordinates.
     * This is used for quick lookup in maps and sets.
     */
    private String getArtifactKey(MavenProject project) {
        return project.getGroupId() + ":" + 
               project.getArtifactId() + ":" + 
               project.getVersion();
    }

    /**
     * Generate a simple key for identifying an artifact by its GAV coordinates.
     */
    private String getArtifactKey(Artifact artifact) {
        return artifact.getGroupId() + ":" + 
               artifact.getArtifactId() + ":" + 
               artifact.getVersion();
    }

    /**
     * Check if an artifact file is invalid (null, doesn't exist, or is a directory).
     */
    private boolean isArtifactFileInvalid(File file) {
        return file == null || !file.exists() || file.isDirectory();
    }

    private void embedDirectory(Artifact a) throws IOException, MojoExecutionException {
        embedDirectory(a, a.getFile());
    }

    private void embedDirectory(Artifact a, File dirToWalk) throws IOException, MojoExecutionException {
        ClassportInfo metadata = getMetadata(a);
        AnnotationConstantPool acp = new AnnotationConstantPool(metadata);
        AnnotationConstantPool.ConstantPoolData cpData = acp.getNewEntries();
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

                    if (Arrays.equals(Arrays.copyOfRange(bytes, 0, 4), Utility.MAGIC_BYTES)) {
                        byte[] modifiedCpBytes = acp.injectAnnotation(bytes, cpData);
                        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                            out.write(modifiedCpBytes);
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

        // Build a map of reactor artifacts for quick lookup
        Set<String> reactorArtifactKeys = new HashSet<>();
        if (session.getProjects() != null && session.getProjects().size() > 1) {
            for (MavenProject reactorProject : session.getProjects()) {
                reactorArtifactKeys.add(getArtifactKey(reactorProject));
            }
        }

        getLog().info("Processing dependencies");
        for (Artifact artifact : dependencyArtifacts) {
            // Skip reactor modules in this loop, they will be processed separately
            String artifactKey = getArtifactKey(artifact);
            if (reactorArtifactKeys.contains(artifactKey)) {
                getLog().debug("Skipping reactor module " + artifactKey + " in regular dependency processing");
                continue;
            }

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
        
        // Process reactor modules to enable direct packaging in multi-module projects
        processReactorModules(localrepoRoot);
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

    /*
     * Get the Maven metadata for a reactor module artifact.
     * Uses the reactor project directly instead of trying to build it from the artifact.
     */
    private ClassportInfo getMetadataForReactorModule(Artifact artifact, MavenProject reactorProject) throws IOException {
        String aId = getArtifactLongId(artifact);
        boolean isDirectDependency = this.project.getDependencies().stream().map(dep -> getDependencyLongId(dep))
                .collect(Collectors.toList()).contains(aId);

        return new ClassportHelper().getInstance(
                getArtifactLongId(project.getArtifact()),
                isDirectDependency,
                aId,
                artifact.getArtifactId(),
                artifact.getGroupId(),
                artifact.getVersion(),
                reactorProject.getModel().getDependencies()
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

    /**
     * Process reactor modules to enable direct packaging in multi-module projects.
     * For each reactor module that is a dependency of the current project,
     * copy its artifact and POM to the classport-files directory with embedded metadata.
     * This allows `mvn package -Dmaven.repo.local=classport-files` to work without
     * manual aggregation of classport-files directories.
     * 
     * This method should be called after the package phase to ensure reactor module
     * JARs are available. If called during compile phase, reactor JARs won't exist yet.
     */
    private void processReactorModules(File localrepoRoot) throws MojoExecutionException {
        if (session.getProjects() == null || session.getProjects().size() <= 1) {
            // Not a multi-module build or single module
            return;
        }

        getLog().info("Processing reactor modules for multi-module support");
        
        // Get all reactor projects
        List<MavenProject> reactorProjects = session.getProjects();
        
        // Build a map of reactor artifacts by their coordinates
        Map<String, MavenProject> reactorArtifacts = new HashMap<>();
        for (MavenProject reactorProject : reactorProjects) {
            reactorArtifacts.put(getArtifactKey(reactorProject), reactorProject);
        }
        
        // Check each dependency to see if it's a reactor module
        Set<Artifact> dependencyArtifacts = project.getArtifacts();
        for (Artifact artifact : dependencyArtifacts) {
            String artifactKey = getArtifactKey(artifact);
            
            MavenProject reactorModule = reactorArtifacts.get(artifactKey);
            if (reactorModule != null) {
                // This dependency is a reactor module
                try {
                    getLog().debug("Found reactor module dependency: " + artifactKey);
                    
                    // Try to find the packaged JAR in the target directory
                    // The artifact file might not be set yet if we're in compile phase
                    File reactorArtifactFile = reactorModule.getArtifact().getFile();
                    
                    // If artifact file is not set or is a directory, try to find the JAR in target
                    if (isArtifactFileInvalid(reactorArtifactFile)) {
                        // Try to find the JAR in the target directory
                        File targetDir = new File(reactorModule.getBasedir(), "target");
                        String expectedJarName = reactorModule.getBuild().getFinalName() + ".jar";
                        File potentialJar = new File(targetDir, expectedJarName);
                        
                        if (potentialJar.exists() && potentialJar.isFile()) {
                            reactorArtifactFile = potentialJar;
                            getLog().info("Processing reactor module JAR: " + artifactKey);
                        } else {
                            getLog().debug("Reactor module JAR not found: " + potentialJar.getAbsolutePath() + 
                                         ". Run 'mvn package' first to build reactor modules.");
                            continue;
                        }
                    } else {
                        getLog().info("Processing reactor module: " + artifactKey);
                    }
                    
                    // Get metadata for this reactor module using the reactor project directly
                    ClassportInfo meta = getMetadataForReactorModule(artifact, reactorModule);
                    
                    // Determine the target path in classport-files
                    String artefactPath = getArtefactPath(artifact, true);
                    File artefactFullPath = new File(localrepoRoot + "/" + artefactPath);
                    
                    // Embed metadata in the reactor module artifact
                    JarHelper pkgr = new JarHelper(reactorArtifactFile,
                            artefactFullPath,
                            /* overwrite target if exists? */ true);
                    pkgr.embed(meta);
                    
                    // Copy the POM file with proper error handling
                    File pomFile = reactorModule.getFile();
                    File pomDestFile = new File(artefactFullPath.getAbsolutePath().replace(".jar", ".pom"));
                    if (pomFile != null && pomFile.isFile()) {
                        // Ensure the destination directory exists
                        pomDestFile.getParentFile().mkdirs();
                        
                        if (!pomDestFile.exists()) {
                            try {
                                Files.copy(pomFile.toPath(), pomDestFile.toPath());
                            } catch (IOException copyError) {
                                getLog().warn("Failed to copy POM file for reactor module " + artifactKey + ": " + copyError.getMessage());
                            }
                        }
                    }
                    
                    getLog().info("Embedded reactor module artifact to: " + artefactFullPath);
                } catch (IOException e) {
                    getLog().error("Failed to process reactor module " + artifactKey + ": " + e);
                }
            }
        }
    }
}
