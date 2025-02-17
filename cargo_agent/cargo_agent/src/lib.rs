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
    if jni_env.is_null() || thread.is_null() || method.is_null() || jvmti_env.is_null() {
        eprintln!("Error: One or more pointers are null in method_entry_callback");
        return;
    }

    // Pointers because we are going to modify the values. 
    let mut name: *mut ::std::os::raw::c_char = std::ptr::null_mut();
    let mut signature: *mut ::std::os::raw::c_char = std::ptr::null_mut();
    let mut generic: *mut ::std::os::raw::c_char = std::ptr::null_mut();
    let mut class: jclass = std::ptr::null_mut();

    // deference the pointer to get the value and call the GetMethodName function.
    // &mut name is a mutable reference to the pointer name. It is a reference because we are going to modify the value of the pointer.
    let result = unsafe {
        (**jvmti_env).GetMethodName.unwrap()(jvmti_env, method, &mut name, &mut signature, &mut generic)
    };
    if result != jvmtiError_JVMTI_ERROR_NONE as u32 {
        eprintln!("Error: Unable to get method name in method_entry_callback");
        return;
    }

    // Get the class of the method
    let result = unsafe {
        (**jvmti_env).GetMethodDeclaringClass.unwrap()(jvmti_env, method, &mut class)
    };
    
    if result != jvmtiError_JVMTI_ERROR_NONE as u32 {
        eprintln!("Error: Unable to get declaring class in method_entry_callback");
        return;
    }
    
    // "std::ffi::CStr::from_ptr(name).to_string_lossy()" is used to convert the C string to a Rust string.
    if !name.is_null() {
        let name_str = unsafe { std::ffi::CStr::from_ptr(name).to_string_lossy() };
        callGetAnnotation(jvmti_env, jni_env, &name_str, class);
    }

    if !signature.is_null() {
        unsafe { (**jvmti_env).Deallocate.unwrap()(jvmti_env, signature as *mut _); }
    }

    if !generic.is_null() {
        unsafe { (**jvmti_env).Deallocate.unwrap()(jvmti_env, generic as *mut _); }
    }

    if !class.is_null() {
        unsafe { (**jni_env).DeleteLocalRef.unwrap()(jni_env, class); }
    }
}

fn callGetAnnotation(jvmti_env: *mut jvmtiEnv, jni_env: *mut JNIEnv, method: &str, class: jclass) {
    // Disable the METHOD_ENTRY event to avoid infinite recursion because we invoke callObjectMethod in the callback which triggers method entry event.
    let result = disable_event(jvmti_env, jvmtiEvent_JVMTI_EVENT_METHOD_ENTRY, jvmtiEventMode_JVMTI_DISABLE);
    if result == false {
        eprintln!("Error: Unable to disable METHOD_ENTRY event");
        return;
    }

    let class_class_name = std::ffi::CString::new("java/lang/Class").unwrap();
    let class_class = unsafe { 
        (**jni_env).FindClass.unwrap()(jni_env, class_class_name.as_ptr()) 
    };
    if class_class.is_null() {
        eprintln!("Error: Unable to find class java/lang/Class in method_entry_callback");
        return;
    }   

    let classport_info_class_name = std::ffi::CString::new("io/github/chains_project/classport/commons/ClassportInfo").unwrap();
    let classport_info_class = unsafe {
        (**jni_env).FindClass.unwrap()(jni_env, classport_info_class_name.as_ptr())
    };
    if classport_info_class.is_null() {
        eprintln!("Error: Unable to find class classportInfo in method_entry_callback");
        return;
    }

    let getAnnotation_method_name = std::ffi::CString::new("getAnnotation").unwrap();
    let getAnnotation_signature = std::ffi::CString::new("(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;").unwrap();

    let methodID = unsafe {
        (**jni_env).GetMethodID.unwrap()(jni_env, class_class, getAnnotation_method_name.as_ptr(), getAnnotation_signature.as_ptr())     
    };

    if methodID.is_null() {
        eprintln!("Error: Unable to find method getAnnotation in method_entry_callback");
        return;
    }

    let annotation = unsafe {
        (**jni_env).CallObjectMethod.unwrap()(jni_env, class, methodID, classport_info_class)
    };

    if !annotation.is_null() {
        let toString_method_name = std::ffi::CString::new("toString").unwrap();
        let toString_signature = std::ffi::CString::new("()Ljava/lang/String;").unwrap();
        let toString_methodID = unsafe {
            (**jni_env).GetMethodID.unwrap()(jni_env, class_class, toString_method_name.as_ptr(), toString_signature.as_ptr())
        };

        if !toString_methodID.is_null() {
            let annotation_string = unsafe {
                (**jni_env).CallObjectMethod.unwrap()(jni_env, annotation, toString_methodID) as jstring
            };

            if !annotation_string.is_null() {
                let mut is_copy: jboolean = 0;
                let chars = unsafe {
                    (**jni_env).GetStringUTFChars.unwrap()(jni_env, annotation_string, &mut is_copy)
                };

                if !chars.is_null() {
                    let annotation_str = unsafe { std::ffi::CStr::from_ptr(chars).to_string_lossy().into_owned() };
                    println!("Annotation: {}", annotation_str);

                    unsafe {
                        (**jni_env).ReleaseStringUTFChars.unwrap()(jni_env, annotation_string, chars);
                    }
                }
            }
        }    
    }

    // Enable the METHOD_ENTRY event again
    let result = enable_event(jvmti_env, jvmtiEvent_JVMTI_EVENT_METHOD_ENTRY, jvmtiEventMode_JVMTI_ENABLE);
    if result == false {
        eprintln!("Error: Unable to enable METHOD_ENTRY event");
    }
}

#[no_mangle]
pub extern "C" fn vminit(jvmti_env: *mut jvmtiEnv, jni_env: *mut JNIEnv, thread: jthread) {
    println!("VM init event, live phase.");  
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

    // Get the JVMTI environment pointer
    let result = unsafe { (**vm).GetEnv.unwrap()(vm, &mut jvmti as *mut _ as *mut _, JVMTI_VERSION_1_0 as jint) };
    if result != jvmtiError_JVMTI_ERROR_NONE as jint {
        return result;
    }

    if jvmti.is_null() {
        eprintln!("Error: jvmtiEnv pointer is null after GetEnv in Agent_OnLoad");
        return jvmtiError_JVMTI_ERROR_NULL_POINTER as jint;
    }
    // result = (**jvmti).GetPotentialCapabilities.unwrap()(jvmti, &capabilities);
    capabilities.set_can_generate_method_entry_events(1);
    capabilities.set_can_get_constant_pool(1); 

    unsafe { (**jvmti).AddCapabilities.unwrap()(jvmti, &capabilities); }
    if result != (jvmtiError_JVMTI_ERROR_NONE as jint).try_into().unwrap() {
        eprintln!("Error: Unable to add capabilities in Agent_OnLoad");
        return result.try_into().unwrap();
    }

    callbacks.MethodEntry = Some(method_entry_callback);
    callbacks.VMInit = Some(vminit);
    let result = unsafe { (**jvmti).SetEventCallbacks.unwrap()(jvmti, &callbacks, std::mem::size_of::<jvmtiEventCallbacks>() as jint) };
    if result != (jvmtiError_JVMTI_ERROR_NONE as jint).try_into().unwrap() {
        eprintln!("Error: Unable to set event callbacks in Agent_OnLoad");
        return result.try_into().unwrap();
    }

    if enable_event(jvmti, jvmtiEvent_JVMTI_EVENT_METHOD_ENTRY, jvmtiEventMode_JVMTI_ENABLE) == false {
        return JNI_ERR;
    }

    if enable_event(jvmti, jvmtiEvent_JVMTI_EVENT_VM_INIT, jvmtiEventMode_JVMTI_ENABLE) == false {
        return JNI_ERR;
    }

    let jar_path = std::ffi::CString::new("../../classport-commons/target/classport-commons-0.1.0-SNAPSHOT.jar").unwrap();
    let error = unsafe {(**jvmti).AddToBootstrapClassLoaderSearch.unwrap()(jvmti, jar_path.as_ptr())};
    if error != (jvmtiError_JVMTI_ERROR_NONE as jint).try_into().unwrap(){
        eprintln!("Error: Unable to add jar to bootstrap class loader search: {}", error);
    } else {
        println!("Jar added to bootstrap class loader search");
    }

    println!("Agent loaded with options: {:?}", options);
    
    0 // JNI_OK
}

fn enable_event(jvmti: *mut jvmtiEnv, event_type: jvmtiEvent, mode: jvmtiEventMode) -> bool {
    let result = unsafe { (**jvmti).SetEventNotificationMode.unwrap()(jvmti, mode, event_type as u32, std::ptr::null_mut()) };
    if result != jvmtiError_JVMTI_ERROR_NONE {
        eprintln!("Error: Unable to enable event: {}", result);
        return false;
    } else {
        return true;
    }
}

fn disable_event(jvmti: *mut jvmtiEnv, event_type: jvmtiEvent, mode: jvmtiEventMode) -> bool {
    let result = unsafe { (**jvmti).SetEventNotificationMode.unwrap()(jvmti, mode, event_type as u32, std::ptr::null_mut()) };
    if result != jvmtiError_JVMTI_ERROR_NONE {
        eprintln!("Error: Unable to disable event: {}", result);
        return false;
    } else {
        return true;
    }
}

#[no_mangle]
pub extern "C" fn Agent_OnUnload(vm: *mut JavaVM) {
    if vm.is_null() {
        eprintln!("Error: JavaVM pointer is null in Agent_OnUnload");
        return;
    }
    println!("Agent unloaded");
}

