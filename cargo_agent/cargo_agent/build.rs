use std::env;
use std::path::PathBuf;

fn main() {
    println!("OUT_DIR: {}", env::var("OUT_DIR").unwrap());

    let jvm_lib_path = env::var("JVM_LIB_PATH").unwrap_or_else(|_| "/usr/local/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/lib/server".to_string());
    let jvm_include_path = env::var("JVM_INCLUDE_PATH").unwrap_or_else(|_| "/usr/local/opt/openjdk@23/include/jvmti.h".to_string());
    let jni_include_path = env::var("JNI_INCLUDE_PATH").unwrap_or_else(|_| "/usr/local/opt/openjdk@23/include/jni.h".to_string());

    println!("cargo:rustc-link-search={}", jvm_lib_path);
    println!("cargo:rustc-link-lib=jvm");

    let bindings = bindgen::Builder::default()
        .header(jvm_include_path)
        .header(jni_include_path)
        .blocklist_function("Agent_OnLoad")
        .blocklist_function("Agent_OnUnload")
        // Tell cargo to invalidate the built crate whenever any of the
        // included header files changed.
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        .generate()
        .expect("Unable to generate bindings");

    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings!");
}