# Agent using Instrumentation API
This Agent uses Instrumentation API to instrument the loaded classes.

It can intercept the methods actually used in an application and create CSV files with information related to the method's name, declaring class, and all the information related to the dependency it belongs to, such as the GAV and the parent and child dependencies.

## How it works
1. The Java Agent intercepts the loading class
2. It ignores it if it is not annotated (TODO: can we infer something?). If it is, it starts processing it.
3. The `MethodInterceptorVisitor` class is used to visit and process the bytecode of loaded classes.
4. For each method in the class, the `MethodInterceptor` injects a custom bytecode at the beginning of the method.
5. The injected code constructs a string containing:
   - The class name
   - The method name
   - Dependency metadata, including GAV (Group, Artifact, Version), parent/child dependencies, and project ID.

    It passes this metadata to a static `addToQueue` method, which stores it in a queue.
6. This queue is periodically flushed and the information is written to a CSV file.

## How to use it

After packaging the program:

```console
java -javaagent:<path-to-agent-jar> -jar <path-to-app-to-be-analyzed> [options related to the analyzing app]
```

An `output.csv`file is created into the folder.