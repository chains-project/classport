# Classport

## About

Classport is a number of tools used with JAR files to embed and extract supply chain-related metadata.
This metadata is stored in an annotation, contained in the `classport.commons` package. This
repository contains an example implementation in the form of a Maven plugin that
modifies the JAR files during the build process. These can then be packaged
together with the application, or just added to the class path in place of the
"regular" versions of the class files.

The repository also contains tools for extracting the data. The `classport.agent`
package contains a Java agent that can be run alongside the Classport-ified JAR.
This agent logs all the supply-chain data for classes that get loaded,
and prints a dependency tree from these at the end. This way, the dependency tree
will consist of only those dependencies that were actually used at runtime.

Finally, the `classport.analyser` package contains two tools for statically analysing
and modifying JAR files.

## Usage

The `classport` goal will retrieve a list of project dependencies and their
corresponding JAR files, embed the annotation into all class files within each
JAR, and recreate what can be seen as a dependency-only Maven local repository
in the `classport-files` directory. The JAR files from within there can then be
included in the class path with the `-cp` flag as per usual.

For projects that get packaged into an Uber-JAR, add `-Dmaven.repo.local=classport-files`
to your `mvn package` flags, as this will package the project using the amended files.
For multi-module projects, package each project separately as dependency properties
may differ (e.g. a direct dependency for one module is a transitive one for another).

Finally, run the packaged JAR using the `-javaagent:path-to-classport-agent` flag.

## The thesis behind this project
Link TBA
