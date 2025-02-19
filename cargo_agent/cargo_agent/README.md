## Rust Agent 
Here is an agent in Rust.

* lib.rs is the agent that intercepts the executing dependencies and extracts the software supply chain information from them by reading the embedded annotations. To reach this goal, it intercepts every [Method Entry event](https://docs.oracle.com/en/java/javase/20/docs/specs/jvmti.html#MethodEntry);
* build.rs is the code that generates the binding of jvmti.h and jni.h. It uses [rust-bindgen](https://rust-lang.github.io/rust-bindgen/) 

### How to use it

- requirements

See [bindgen guide](https://rust-lang.github.io/rust-bindgen/requirements.html).

``` 
Required versions:
- bindgen 0.71.1
- Rust latest --> bindgen points always to the latest available Rust version
```

- in build.rs substitute the path with the path where the resources are

- compile the project:

`cargo build --release`

The generated binding is here cargo_agent/cargo_agent/target/debug/build/cargo_agent-a1019fb4d4c51e4b/out/bindings.rs


- run the agent:

`java -agentpath:/path/to/target/release/libmy_java_agent.dylib -jar your_app.jar`

The command outputs a CSV file (annotations.csv) with the detected dependencies.

For each dependency, the following information is displayed:
- method -> intercepted method
- class -> class that declares the method
- sourceProjectId -> id of the parent project
- isDirectDependency -> boolean that indicates if the dependency is direct or transitive
- id -> id of the dependency that the method belongs to
- artefact -> artefact of the dependency that the method belongs to
- group -> group of the dependency that the method belongs to
- version -> version of the dependency that the method belongs to
- childIds -> ids of direct dependencies of the dependency that the method belongs to



