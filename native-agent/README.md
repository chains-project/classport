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