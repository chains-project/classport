#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]

include!(concat!(env!("OUT_DIR"), "/bindings.rs"));

// Constant pool tags
const CONSTANT_UTF8: u8 = 1;
const CONSTANT_INTEGER: u8 = 3;
const CONSTANT_FLOAT: u8 = 4;
const CONSTANT_LONG: u8 = 5;
const CONSTANT_DOUBLE: u8 = 6;
const CONSTANT_CLASS: u8 = 7;
const CONSTANT_STRING: u8 = 8;
const CONSTANT_FIELDREF: u8 = 9;
const CONSTANT_METHODREF: u8 = 10;
const CONSTANT_INTERFACEMETHODREF: u8 = 11;
const CONSTANT_NAME_AND_TYPE: u8 = 12;
const CONSTANT_METHODHANDLE: u8 = 15;
const CONSTANT_METHOD_TYPE: u8 = 16;
const CONSTANT_INVOKE_DYNAMIC: u8 = 18;
const CONSTANT_MODULE: u8 = 19;
const CONSTANT_PACKAGE: u8 = 20;

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
    let mut class: jclass = std::ptr::null_mut();
    let mut constant_pool_count: jint = 0;
    let mut constant_pool_byte_count: jint = 0;
    let mut constant_pool: *mut u8 = std::ptr::null_mut();

    // deference the pointer to get the value and call the GetMethodName function.
    // &mut name is a mutable reference to the pointer name. It is a reference because we are going to modify the value of the pointer.
    if !jvmti_env.is_null() {
        unsafe {
            (**jvmti_env).GetMethodName.unwrap()(jvmti_env, method, &mut name, &mut signature, &mut generic);
        }
    } 
    else {
        eprintln!("Error: jvmtiEnv pointer is null in method_entry_callback");
        return;
    }
    
    
    // "std::ffi::CStr::from_ptr(name).to_string_lossy()" is used to convert the C string to a Rust string.
    if !name.is_null() {
        let name_str = unsafe { std::ffi::CStr::from_ptr(name).to_string_lossy() };
        if name_str == "capitalize" {
            println!("Method entry: {}", name_str);
        }
        unsafe {
            (**jvmti_env).Deallocate.unwrap()(jvmti_env, name as *mut _);
        }
    }

    if !signature.is_null() {
        unsafe { (**jvmti_env).Deallocate.unwrap()(jvmti_env, signature as *mut _); }
    }

    if !generic.is_null() {
        unsafe { (**jvmti_env).Deallocate.unwrap()(jvmti_env, generic as *mut _); }
    }
    
    if !jvmti_env.is_null() {
        let result = unsafe {
            (**jvmti_env).GetMethodDeclaringClass.unwrap()(jvmti_env, method, &mut class)
        };
        if result != jvmtiError_JVMTI_ERROR_NONE as u32 {
            eprintln!("Error: Unable to get declaring class in method_entry_callback");
            return;
        }
    
        let result = unsafe {
            (**jvmti_env).GetConstantPool.unwrap()(jvmti_env, class, &mut constant_pool_count, &mut constant_pool_byte_count, &mut constant_pool)
        };

        if result != jvmtiError_JVMTI_ERROR_NONE as u32 {
            eprintln!("Error: Unable to get constant pool in method_entry_callback, error code: {}", result);
            return;
        }
    
        parse_constant_pool(constant_pool, constant_pool_byte_count);
    
        // Deallocate the constant pool memory
        if !constant_pool.is_null() {
            unsafe {
                (**jvmti_env).Deallocate.unwrap()(jvmti_env, constant_pool);
            }
        }
    }
    
}

fn parse_constant_pool(constant_pool: *mut u8, constant_pool_byte_count: jint) {
    if constant_pool.is_null() {
        eprintln!("Error: Constant pool pointer is null in parse_constant_pool");
        return;
    }

    let mut offset: isize = 0;
    while offset < constant_pool_byte_count as isize {
        let tag = unsafe { *constant_pool.offset(offset) };
        offset += 1;

        match tag {
            CONSTANT_CLASS | CONSTANT_STRING | CONSTANT_METHOD_TYPE | CONSTANT_MODULE | CONSTANT_PACKAGE => {
                offset += 2; // Skip 2 bytes
            }
            CONSTANT_INTEGER | CONSTANT_FLOAT | CONSTANT_FIELDREF | CONSTANT_METHODREF | CONSTANT_INTERFACEMETHODREF | CONSTANT_NAME_AND_TYPE | CONSTANT_INVOKE_DYNAMIC => {
                offset += 4; // Skip 4 bytes
            }
            CONSTANT_LONG | CONSTANT_DOUBLE => {
                offset += 8; // Skip 8 bytes
            }
            CONSTANT_UTF8 => {
                if offset + 2 >= constant_pool_byte_count as isize {
                    eprintln!("Error: Offset {} out of bounds (constant pool byte count: {})", offset + 2, constant_pool_byte_count);
                    std::process::exit(1);
                }
                let length = unsafe {
                    (*constant_pool.offset(offset) as u16) << 8 | (*constant_pool.offset(offset + 1) as u16)
                };
                offset += 2 + length as isize; // Skip length bytes + 2 bytes
            }
            CONSTANT_METHODHANDLE => {
                offset += 3; // Skip 3 bytes
            }
            _ => {
                eprintln!("Error: Unknown tag {} in constant pool at entry", tag);
                std::process::exit(1);
            }
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
            capabilities.set_can_get_constant_pool(1); 
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

