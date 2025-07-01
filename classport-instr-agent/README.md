# Classport Introspector
This module implements the **Classport Introspector**, a Java agent that performs **runtime dependency introspection**.  
It extracts build-time dependency metadata (GAV: Group, Artifact, Version) embedded in `.class` files using the [Classport Embedder](../maven-plugin/), and records which dependencies are actually used **during execution**.


## How it works

1. **Agent Attachment**: The Introspector is attached to the JVM using the `-javaagent` option.
2. **Instrumentation**: It instruments the **methods** of application classes at load time.
3. **Annotation Extraction**: When an instrumented method executes, the agent extracts the `@ClassportInfo` annotation from the class.
4. **Efficient Tracking**: To reduce overhead:
   - Each dependency (GAV) is recorded **only once**.
   - Once a class’s GAV is seen, its future methods are **excluded** from instrumentation.
5. **Shutdown Hook**: On JVM termination, the final list of used dependencies is written to a CSV file.


## Usage

After embedding:

```console
java -javaagent:<path-to-agent-jar>=<name-of-the-project>,<output-location-path>,dependency -jar <path-to-app-jar-to-be-analyzed> [options related to the analyzing app]
```

Note: ensure that the output folder exists before running the command.

A CSV file with the list of runtime dependencies is created into the folder.