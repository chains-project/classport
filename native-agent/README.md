# Native Agent
This is the native agent.

It detects the executing class and extracts the embedded information from the class files.

To use it:
- Compile:
```console
make
````

- Execute it against a target Java application:
```console
java -agentpath:agent.so -jar <path-to-jar-target-application>
```

The agent outputs the information extracted from the embedded annotation of the **executing class**.

An example of the output is:

```
Found custom annotation: Lio/github/chains_project/classport/commons/ClassportInfo;
Class: Lorg/apache/commons/lang3/StringUtils;
group: org.apache.commons
version: 3.17.0
id: org.apache.commons:commons-lang3:jar:3.17.0
artefact: commons-lang3
sourceProjectId: com.example:test-agent-app:jar:1.0-SNAPSHOT
childIds: org.apache.commons:commons-text:jar:1.12.0
```