# maven-plugin
## Embedder 
The Embedder is responsible for the Embedding phase of Classport. It is implemented through a Maven Plugin.

The Embedder performs the following tasks:
* Retrieval of direct and transitive dependencies through `pom.xml` analysis 
* Download of dependencies' JARs locally
* For each JAR, extraction of files
* Processing of each file from the JAR, different behavior according to the different file to handle:
    * Class files: it copies them into a new JAR, embedding any supply chain information in the process.
    * Manifest: all signature-related entries are removed from the manifest, and the remainder of the file is copied over to the new JAR.
    * Signature files: they are ignored
    * Others: copied to the new JAR without modification
* Repackaging of the embedded class files into a new JAR 
* Saving the new JARs in the `classport-files` folder

The `classport-files` should be used by the user of the tool as a new Maven repository during the packaging phase adding `-Dmaven.repo.local=classport-files` to `mvn package` command.

This module contains three files: 

* `EmbeddingMojo.java` 
It is the implementation of the Maven plugin. It declares the `embed` goal.
* `JarHelper.java`
It contains the methods for supporting the plugin.
* `MetadataAdder.java`
It is class for adding metadata to class files using ASM.


