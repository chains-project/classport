#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]

include!(concat!(env!("OUT_DIR"), "/bindings.rs"));

extern "C" fn method_entry_callback(
    jvmti_env: *mut jvmtiEnv,
    jni_env: *mut JNIEnv,
    thread: jthread,
    method: jmethodID,
) {
    unsafe {
        let mut name: *mut ::std::os::raw::c_char = std::ptr::null_mut();
        let mut signature: *mut ::std::os::raw::c_char = std::ptr::null_mut();
        let mut generic: *mut ::std::os::raw::c_char = std::ptr::null_mut();

        (**jvmti_env).GetMethodName.unwrap()(jvmti_env, method, &mut name, &mut signature, &mut generic);

        if !name.is_null() && std::ffi::CStr::from_ptr(name).to_string_lossy() == "capitalize" {
            println!(
                "Method entry: {}",
                std::ffi::CStr::from_ptr(name).to_string_lossy()
            );
            (**jvmti_env).Deallocate.unwrap()(jvmti_env, name as *mut _);
        }

        if !signature.is_null() {
            (**jvmti_env).Deallocate.unwrap()(jvmti_env, signature as *mut _);
        }

        if !generic.is_null() {
            (**jvmti_env).Deallocate.unwrap()(jvmti_env, generic as *mut _);
        }
    }
}

#[no_mangle]
pub extern "C" fn Agent_OnLoad(vm: *mut JavaVM, options: *mut ::std::os::raw::c_char, reserved: *mut ::std::os::raw::c_void) -> jint {
    unsafe {
        let mut jvmti: *mut jvmtiEnv = std::ptr::null_mut();
        let mut capabilities: jvmtiCapabilities = std::mem::zeroed();
        let mut callbacks: jvmtiEventCallbacks = std::mem::zeroed();

        let result = (**vm).GetEnv.unwrap()(vm, &mut jvmti as *mut _ as *mut _, JVMTI_VERSION_1_0 as jint);
        if result != jvmtiError_JVMTI_ERROR_NONE as jint {
            return result;
        }

        // result = (**jvmti).GetPotentialCapabilities.unwrap()(jvmti, &capabilities);
        capabilities.set_can_generate_method_entry_events(1);
        (**jvmti).AddCapabilities.unwrap()(jvmti, &capabilities);

        
        callbacks.MethodEntry = Some(method_entry_callback);
        (**jvmti).SetEventCallbacks.unwrap()(jvmti, &callbacks, std::mem::size_of::<jvmtiEventCallbacks>() as jint);

        (**jvmti).SetEventNotificationMode.unwrap()(jvmti, jvmtiEventMode_JVMTI_ENABLE as jvmtiEventMode, jvmtiEvent_JVMTI_EVENT_METHOD_ENTRY as u32, std::ptr::null_mut());

        println!("Agent loaded with options: {:?}", options);
    }
    0 // JNI_OK
}

#[no_mangle]
pub extern "C" fn Agent_OnUnload(vm: *mut JavaVM) {
    // Pulizia dell'agente
    println!("Agent unloaded");
}

