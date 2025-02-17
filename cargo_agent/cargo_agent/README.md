## Rust Agent (WIP)
Here is an agent in Rust.

* lib.rs is the agent (for the moment just intercept onMethodEntry event and print "capitalize" when this method is intercepted);
* build.rs is the code that generate the binding of jvmti.h. It uses [rust-bindgen](https://rust-lang.github.io/rust-bindgen/) 

### To use it

Requirements: see [bindgen guide](https://rust-lang.github.io/rust-bindgen/requirements.html).

- set the required environment variables

```
export JVM_LIB_PATH=<path-to-your-jdk-instalaltion>/libexec/openjdk.jdk/Contents/Home/lib/server
export JVM_INCLUDE_PATH=<path-to-jvmti.h> 
export JNI_INCLUDE_PATH=<path-to-jni.h>
```

- compile the project:

`cargo build --release`

- run the agent:

`java -agentpath:/path/to/target/release/libmy_java_agent.dylib -jar your_app.jar`


