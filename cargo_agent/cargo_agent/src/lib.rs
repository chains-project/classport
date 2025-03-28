#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]

use std::fs::OpenOptions;
use std::io::{Write, BufWriter};
use std::fs;
use std::path::Path;

use std::collections::HashMap;
use std::sync::{Mutex, Once};

include!(concat!(env!("OUT_DIR"), "/bindings.rs"));

static mut ANNOTATION_CACHE: Option<Mutex<HashMap<String, Vec<Vec<String>>>>> = None;
static INIT: Once = Once::new();

fn get_annotation_cache() -> &'static Mutex<HashMap<String, Vec<Vec<String>>>> {
    unsafe {
        INIT.call_once(|| {
            ANNOTATION_CACHE = Some(Mutex::new(HashMap::new()));
        });
        ANNOTATION_CACHE.as_ref().unwrap()
    }
}

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

    // get the class name of the method
    let mut class_name: *mut ::std::os::raw::c_char = std::ptr::null_mut();
    let result = unsafe {
        (**jvmti_env).GetClassSignature.unwrap()(jvmti_env, class, &mut class_name, std::ptr::null_mut())
    };
    if result != jvmtiError_JVMTI_ERROR_NONE as u32 {
        eprintln!("Error: Unable to get class name in method_entry_callback");
        return;
    }

    
    // "std::ffi::CStr::from_ptr(name).to_string_lossy()" is used to convert the C string to a Rust string.
    if !name.is_null() && !class_name.is_null() {
        let name_str = unsafe { std::ffi::CStr::from_ptr(name).to_string_lossy() };
        let class_name_str = unsafe { std::ffi::CStr::from_ptr(class_name).to_string_lossy() };
        // Disable the METHOD_ENTRY event to avoid infinite recursion because we invoke callObjectMethod in the callback which triggers method entry event.
        let result = disable_event(jvmti_env, jvmtiEvent_JVMTI_EVENT_METHOD_ENTRY, jvmtiEventMode_JVMTI_DISABLE);
        if result == false {
            eprintln!("Error: Unable to disable METHOD_ENTRY event");
            return;
        }

        if let Some(mut annotation_data) = get_annotation_data(&class_name_str) {
            // modify annotaion_data to insert the new method name 
            if let Some(row) = annotation_data.first_mut() {
                if let Some(method_name) = row.first_mut() {
                    *method_name = name_str.to_string();
                } else {
                    eprintln!("Error: Unable to modify method name in method_entry_callback");
                    return;
                }
            } else {
                eprintln!("Error: Unable to modify method name in method_entry_callback");
                return;
            }

            let filename = "annotations.csv";
            save_to_csv(filename, annotation_data).expect("Failed to write CSV file");
        } else {
            call_getAnnotation(jni_env, &name_str, &class_name_str, class);
        }


        // Enable the METHOD_ENTRY event again
        let result = enable_event(jvmti_env, jvmtiEvent_JVMTI_EVENT_METHOD_ENTRY, jvmtiEventMode_JVMTI_ENABLE);
        if result == false {
            eprintln!("Error: Unable to enable METHOD_ENTRY event");
            return;
        }
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

fn call_getAnnotation(jni_env: *mut JNIEnv, method: &str, class_name: &str, class: jclass) {
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
        let annotation_data = get_annotation_values(method, class_name, annotation, jni_env, classport_info_class, class_class);
        if annotation_data.is_empty() {
            eprintln!("Error: Unable to get annotation data in method_entry_callback");
            return;
        }
        insert_annotation_data(class_name.to_string(), annotation_data.clone());
        let filename = "annotations.csv";
        save_to_csv(filename, annotation_data).expect("Failed to write CSV file");

    }
    
    if !annotation.is_null() {
        unsafe { (**jni_env).DeleteLocalRef.unwrap()(jni_env, annotation); }
    }
    
}

fn get_annotation_values(method: &str, class_name: &str, annotation: jobject, jni_env: *mut JNIEnv, classport_info_class: jclass, class_class: jclass) -> Vec<Vec<String>> {
    let source_project_id_name = std::ffi::CString::new("sourceProjectId").unwrap();
    let source_project_id_signature = std::ffi::CString::new("()Ljava/lang/String;").unwrap();

    let is_direct_dep_name = std::ffi::CString::new("isDirectDependency").unwrap();
    let is_direct_dep_signature = std::ffi::CString::new("()Z").unwrap();

    let id_name = std::ffi::CString::new("id").unwrap();
    let id_signature = std::ffi::CString::new("()Ljava/lang/String;").unwrap();

    let artefact_name = std::ffi::CString::new("artefact").unwrap();
    let artefact_signature = std::ffi::CString::new("()Ljava/lang/String;").unwrap();

    let group_name = std::ffi::CString::new("group").unwrap();
    let group_signature = std::ffi::CString::new("()Ljava/lang/String;").unwrap();

    let version_name = std::ffi::CString::new("version").unwrap();
    let version_signature = std::ffi::CString::new("()Ljava/lang/String;").unwrap();

    let child_ids_name = std::ffi::CString::new("childIds").unwrap();
    let child_ids_signature = std::ffi::CString::new("()[Ljava/lang/String;").unwrap();

    let mut annotation_data: Vec<Vec<String>> = Vec::new();

    let mut row = Vec::new();
    row.push(method.to_string());
    row.push(class_name.to_string());
    row.push(get_annotation_value(jni_env, classport_info_class, annotation, class_class, source_project_id_name, source_project_id_signature, "sourceProjectId").unwrap_or("".to_string()));
    row.push(get_annotation_value(jni_env, classport_info_class, annotation, class_class, is_direct_dep_name, is_direct_dep_signature, "isDirectDependency").unwrap_or("false".to_string()));
    row.push(get_annotation_value(jni_env, classport_info_class, annotation, class_class, id_name, id_signature, "id").unwrap_or("".to_string()));
    row.push(get_annotation_value(jni_env, classport_info_class, annotation, class_class, artefact_name, artefact_signature, "artefact").unwrap_or("".to_string()));
    row.push(get_annotation_value(jni_env, classport_info_class, annotation, class_class, group_name, group_signature, "group").unwrap_or("".to_string()));
    row.push(get_annotation_value(jni_env, classport_info_class, annotation, class_class, version_name, version_signature, "version").unwrap_or("".to_string()));
    row.push(get_annotation_value(jni_env, classport_info_class, annotation, class_class, child_ids_name, child_ids_signature, "childIds").unwrap_or("".to_string()));

    annotation_data.push(row);

    return annotation_data;
}

fn save_to_csv(filename: &str, data: Vec<Vec<String>>) -> std::io::Result<()> {
    let file = OpenOptions::new()
        .create(true)  // Create if missing
        .append(true)  // Append instead of overwriting
        .open(filename)?; // Open file

    let is_empty = file.metadata()?.len() == 0;  // Check before moving `file`

    let mut writer = BufWriter::new(file);

    // Write header only if file was empty
    if is_empty {
        writeln!(writer, "method,class,sourceProjectId,isDirectDependency,id,artefact,group,version,childIds")?;
    }

    // Write new data rows
    for row in data {
        writeln!(writer, "{}", row.join(","))?;
        // println!("{:?}", row);
    }


    Ok(())
}

pub fn get_annotation_value(
    jni_env: *mut JNIEnv,
    classport_info_class: jclass,
    annotation: jobject,
    class_class: jclass,
    name: std::ffi::CString,
    signature: std::ffi::CString,
    value: &str,
) -> Option<String> {
    let get_annotation_value = unsafe {
        (**jni_env).GetMethodID.unwrap()(jni_env, classport_info_class, name.as_ptr(), signature.as_ptr())
    };
    if get_annotation_value.is_null() {
        eprintln!("Error: Unable to get method {}() in get_annotation_value", value);
        return None;
    }

    if value == "isDirectDependency" {
        let annotation_value = unsafe {
            (**jni_env).CallBooleanMethod.unwrap()(jni_env, annotation, get_annotation_value)
        };
        return Some(if annotation_value == 1 { "true".to_string() } else { "false".to_string() });
    }

    if value == "childIds" {
        let annotation_value = unsafe {
            (**jni_env).CallObjectMethod.unwrap()(jni_env, annotation, get_annotation_value) as jobjectArray
        };
        if annotation_value.is_null() {
            eprintln!("Error: Unable to get annotation value in get_annotation_value");
            return None;
        }

        let length = unsafe { (**jni_env).GetArrayLength.unwrap()(jni_env, annotation_value) };
        let mut child_ids: Vec<String> = Vec::new();

        for i in 0..length {
            let child_id = unsafe { (**jni_env).GetObjectArrayElement.unwrap()(jni_env, annotation_value, i) };
            if !child_id.is_null() {
                let mut is_copy: jboolean = 0;
                let chars = unsafe { (**jni_env).GetStringUTFChars.unwrap()(jni_env, child_id as jstring, &mut is_copy) };

                if !chars.is_null() {
                    let child_id_str = unsafe { std::ffi::CStr::from_ptr(chars).to_string_lossy().into_owned() };
                    child_ids.push(child_id_str);

                    unsafe { (**jni_env).ReleaseStringUTFChars.unwrap()(jni_env, child_id as jstring, chars); }
                }
            }
        }
        return Some(child_ids.join(",")); // Convert Vec<String> into a single comma-separated string
    }


    let annotation_value = unsafe { (**jni_env).CallObjectMethod.unwrap()(jni_env, annotation, get_annotation_value) };
    if annotation_value.is_null() {
        eprintln!("Error: Unable to get annotation value in get_annotation_value");
        return None;
    }

    let toString_method_name = std::ffi::CString::new("toString").unwrap();
    let toString_signature = std::ffi::CString::new("()Ljava/lang/String;").unwrap();
    let toString_methodID = unsafe {
        (**jni_env).GetMethodID.unwrap()(jni_env, class_class, toString_method_name.as_ptr(), toString_signature.as_ptr())
    };

    if !toString_methodID.is_null() {
        let annotation_value_string = unsafe {
            (**jni_env).CallObjectMethod.unwrap()(jni_env, annotation_value, toString_methodID) as jstring
        };
        if !annotation_value_string.is_null() {
            let mut is_copy: jboolean = 0;
            let chars = unsafe {
                (**jni_env).GetStringUTFChars.unwrap()(jni_env, annotation_value_string, &mut is_copy)
            };

            if !chars.is_null() {
                let annotation_str = unsafe { std::ffi::CStr::from_ptr(chars).to_string_lossy().into_owned() };

                unsafe {
                    (**jni_env).ReleaseStringUTFChars.unwrap()(jni_env, annotation_value_string, chars);
                }

                return Some(annotation_str);
            }
        }
    }

    None
}

fn insert_annotation_data(class_name: String, annotation_data: Vec<Vec<String>>) {
    let mut cache = get_annotation_cache().lock().unwrap();
    cache.insert(class_name, annotation_data);
}

fn get_annotation_data(class_name: &str) -> Option<Vec<Vec<String>>> {
    let cache = get_annotation_cache().lock().unwrap();
    cache.get(class_name).cloned()
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

    // check if file annotations.csv exists and delete it
    let filename = "annotations.csv";
    let path = std::path::Path::new(filename);
    if Path::new(path).exists() {
        let _ = fs::remove_file(path);
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

