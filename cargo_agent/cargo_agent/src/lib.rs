#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]

include!(concat!(env!("OUT_DIR"), "/bindings.rs"));

// "extern "C"" is used to tell the Rust compiler to use the C calling convention for the function, 
// because the JVM will call the function using the C calling convention." 
extern "C" fn method_entry_callback(
    jvmti_env: *mut jvmtiEnv,
    jni_env: *mut JNIEnv,
    thread: jthread,
    method: jmethodID,
) {
    if jni_env.is_null() || thread.is_null() || method.is_null() {
        eprintln!("Error: One or more pointers are null in method_entry_callback");
        return;
    }

    // Pointers because we are going to modify the values. 
    let mut name: *mut ::std::os::raw::c_char = std::ptr::null_mut();
    let mut signature: *mut ::std::os::raw::c_char = std::ptr::null_mut();
    let mut generic: *mut ::std::os::raw::c_char = std::ptr::null_mut();

    // deference the pointer to get the value and call the GetMethodName function.
    // &mut name is a mutable reference to the pointer name. It is a reference because we are going to modify the value of the pointer.
    unsafe {
        if !jvmti_env.is_null() {
            (**jvmti_env).GetMethodName.unwrap()(jvmti_env, method, &mut name, &mut signature, &mut generic);
        } 
        else {
            eprintln!("Error: jvmtiEnv pointer is null in method_entry_callback");
            return;
        }
    }

    // "std::ffi::CStr::from_ptr(name).to_string_lossy()" is used to convert the C string to a Rust string.
    if !name.is_null() && unsafe { std::ffi::CStr::from_ptr(name).to_string_lossy() } == "capitalize" {
        println!(
            "Method entry: {}",
            unsafe { std::ffi::CStr::from_ptr(name).to_string_lossy() }
        );
        unsafe { (**jvmti_env).Deallocate.unwrap()(jvmti_env, name as *mut _); }
    }
    unsafe {
        if !signature.is_null() {
            (**jvmti_env).Deallocate.unwrap()(jvmti_env, signature as *mut _);
        }

        if !generic.is_null() {
            (**jvmti_env).Deallocate.unwrap()(jvmti_env, generic as *mut _);
        }
    }
    
}

// no_mangle is used to tell the Rust compiler to not mangle the name of the function, because the JVM will call the function by name.
#[no_mangle]
pub extern "C" fn Agent_OnLoad(vm: *mut JavaVM, options: *mut ::std::os::raw::c_char, _reserved: *mut ::std::os::raw::c_void) -> jint {
    if vm.is_null() {
        eprintln!("Error: JavaVM pointer is null in Agent_OnLoad");
        return jvmtiError_JVMTI_ERROR_NULL_POINTER as jint;
    }

    let mut jvmti: *mut jvmtiEnv = std::ptr::null_mut();
    let mut capabilities: jvmtiCapabilities = unsafe { std::mem::zeroed() };
    let mut callbacks: jvmtiEventCallbacks = unsafe { std::mem::zeroed() };

    let result = unsafe { (**vm).GetEnv.unwrap()(vm, &mut jvmti as *mut _ as *mut _, JVMTI_VERSION_1_0 as jint) };
    if result != jvmtiError_JVMTI_ERROR_NONE as jint {
        return result;
    }

    unsafe {
        if jvmti.is_null() {
            eprintln!("Error: jvmtiEnv pointer is null after GetEnv in Agent_OnLoad");
            return jvmtiError_JVMTI_ERROR_NULL_POINTER as jint;
        }
        else {
            // result = (**jvmti).GetPotentialCapabilities.unwrap()(jvmti, &capabilities);
            capabilities.set_can_generate_method_entry_events(1);
            (**jvmti).AddCapabilities.unwrap()(jvmti, &capabilities);

            callbacks.MethodEntry = Some(method_entry_callback);
            (**jvmti).SetEventCallbacks.unwrap()(jvmti, &callbacks, std::mem::size_of::<jvmtiEventCallbacks>() as jint);

            (**jvmti).SetEventNotificationMode.unwrap()(jvmti, jvmtiEventMode_JVMTI_ENABLE as jvmtiEventMode, jvmtiEvent_JVMTI_EVENT_METHOD_ENTRY as u32, std::ptr::null_mut());
        }
    }
    println!("Agent loaded with options: {:?}", options);
    
    0 // JNI_OK
}

#[no_mangle]
pub extern "C" fn Agent_OnUnload(vm: *mut JavaVM) {
    if vm.is_null() {
        eprintln!("Error: JavaVM pointer is null in Agent_OnUnload");
        return;
    }
    println!("Agent unloaded");
}

