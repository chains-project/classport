#include <jvmti.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>

#define CONSTANT_UTF8 1
#define CONSTANT_INTEGER 3
#define CONSTANT_FLOAT 4
#define CONSTANT_LONG 5
#define CONSTANT_DOUBLE 6
#define CONSTANT_CLASS 7
#define CONSTANT_STRING 8
#define CONSTANT_FIELDREF 9
#define CONSTANT_METHODREF 10
#define CONSTANT_INTERFACEMETHODREF 11
#define CONSTANT_NAME_AND_TYPE 12
#define CONSTANT_METHODHANDLE 15
#define CONSTANT_METHOD_TYPE 16
#define CONSTANT_INVOKE_DYNAMIC 18
#define CONSTANT_MODULE 19
#define CONSTANT_PACKAGE 20

void parse_constant_pool(unsigned char *constant_pool, jint constant_pool_count, char *class_signature, char *method_name) {
    int found_annotation = 0;
    char *value = NULL;
    for (int i = 0; i < constant_pool_count; i++) {
        unsigned char tag = constant_pool[i];

        switch (tag) {
            case CONSTANT_UTF8: {
                uint16_t length;
                memcpy(&length, &constant_pool[i + 1], sizeof(uint16_t));
                length = ntohs(length);
                char *utf8 = (char *)malloc(length + 1);
                memcpy(utf8, &constant_pool[i + 3], length);
                utf8[length] = '\0';
                
                if (strcmp(utf8, "Lio/github/chains_project/classport/commons/ClassportInfo;") == 0) {
                    printf("--------------------\n");
                    printf("Found custom annotation: %s\n", utf8);
                    printf("Class: %s\n", class_signature);
                    printf("Method: %s\n", method_name);
                    found_annotation = 1;
                } else if (strcmp(utf8, "RuntimeVisibleAnnotations") == 0) {
                    found_annotation = 0;
                } else if (found_annotation && strcmp(utf8, "isDirectDependency") != 0) {
                    if (value) {
                        printf("%s: %s\n", value, utf8);
                        free(value);
                        value = NULL;
                    } else {
                        value = strdup(utf8);
                    }
                }
                
                free(utf8);
                i += 2 + length;
                break;
            }
            case CONSTANT_INTEGER:
                i += 4;
                break;
            case CONSTANT_FLOAT:
                i += 4;
                break;
            case CONSTANT_LONG:
                i += 8;
                break;
            case CONSTANT_DOUBLE:
                i += 8;
                break;
            case CONSTANT_CLASS:
            case CONSTANT_STRING:
            case CONSTANT_METHOD_TYPE:
            case CONSTANT_MODULE:
            case CONSTANT_PACKAGE:
                i += 2;
                break;
            case CONSTANT_FIELDREF:
            case CONSTANT_METHODREF:
            case CONSTANT_INTERFACEMETHODREF:
            case CONSTANT_NAME_AND_TYPE:
            case CONSTANT_INVOKE_DYNAMIC:
                i += 4;
                break;
            case CONSTANT_METHODHANDLE:
                i += 3;
                break;
            default:
                fprintf(stderr, "Unknown constant pool type '%d'\n", tag);
                break;
        }
    }
}

// Callback for Method Entry
void JNICALL onMethodEntry(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread, jmethodID method) {
    char *method_name = NULL;
    char *method_signature = NULL;
    char *class_signature = NULL;
    jclass declaring_class;
    jvmtiError err;
    jint constant_pool_count;
    jint constant_pool_byte_count;
    unsigned char *constant_pool;

    // Get the method's declaring class
    err = (*jvmti_env)->GetMethodDeclaringClass(jvmti_env, method, &declaring_class);
    if (err == JVMTI_ERROR_NONE) {
        // Get the class name
        err = (*jvmti_env)->GetClassSignature(jvmti_env, declaring_class, &class_signature, NULL);
    } 

    // Get the method name and signature
    if (err == JVMTI_ERROR_NONE) {
        err = (*jvmti_env)->GetMethodName(jvmti_env, method, &method_name, &method_signature, NULL);
    } 
    // Check if the method name is "customSleepingThread"
    if (err == JVMTI_ERROR_NONE && strcmp(method_name, "customSleepingThread") == 0) {
        printf("Executing customSleepingThread method\n");
        jclass class_class = (*jni_env)->FindClass(jni_env, "java/lang/Class");
        if (class_class == NULL) {
            fprintf(stderr, "Error: Unable to find class java/lang/Class in onMethodEntry\n");
            return;
        }

        jmethodID getAnnotations_method = (*jni_env)->GetMethodID(jni_env, class_class, "getAnnotations", "()[Ljava/lang/annotation/Annotation;");
        if (getAnnotations_method == NULL) {
            fprintf(stderr, "Error: Unable to find method getAnnotations in onMethodEntry\n");
            return;
        }

        jobject annotations = (*jni_env)->CallObjectMethod(jni_env, declaring_class, getAnnotations_method);
        if (annotations == NULL) {
            fprintf(stderr, "Error: Unable to get annotations in onMethodEntry\n");
            return;
        }

        jclass annotationClass = (*jni_env)->FindClass(jni_env, "java/lang/annotation/Annotation");
        jmethodID toStringMethod = (*jni_env)->GetMethodID(jni_env, annotationClass, "toString", "()Ljava/lang/String;");
        if (toStringMethod == NULL) {
            fprintf(stderr, "Error: Unable to find toString method in onMethodEntry\n");
            return;
        }

        jsize annotationCount = (*jni_env)->GetArrayLength(jni_env, annotations);
        for (jsize i = 0; i < annotationCount; i++) {
            jobject annotation = (*jni_env)->GetObjectArrayElement(jni_env, annotations, i);
            jstring annotationStr = (jstring)(*jni_env)->CallObjectMethod(jni_env, annotation, toStringMethod);
            const char *annotationCStr = (*jni_env)->GetStringUTFChars(jni_env, annotationStr, NULL);
            printf("Annotation: %s\n", annotationCStr);
            if (annotationStr) (*jni_env)->ReleaseStringUTFChars(jni_env, annotationStr, annotationCStr);
            if (annotation) (*jni_env)->DeleteLocalRef(jni_env, annotation);
            if (annotationStr) (*jni_env)->DeleteLocalRef(jni_env, annotationStr);
        }
        if (annotations) (*jni_env)->DeleteLocalRef(jni_env, annotations);
        if (annotationClass) (*jni_env)->DeleteLocalRef(jni_env, annotationClass);
    }

    // Print the executing class and method
    // if (err == JVMTI_ERROR_NONE) {
    //     printf("Executing Class: %s, Method: %s%s\n", class_signature, method_name, method_signature);
    // }

    // err = (*jvmti_env)->GetConstantPool(jvmti_env, declaring_class, &constant_pool_count, &constant_pool_byte_count, &constant_pool);
    // if (err != JVMTI_ERROR_NONE) {
    //     fprintf(stderr, "Failed to get constant pool: %d\n", err);
    // }

    // Parse the Constant Pool
    // parse_constant_pool(constant_pool, constant_pool_byte_count, class_signature, method_name);



    // Deallocate JVMTI memory
    if (class_signature) (*jvmti_env)->Deallocate(jvmti_env, (unsigned char *)class_signature);
    if (method_name) (*jvmti_env)->Deallocate(jvmti_env, (unsigned char *)method_name);
    if (method_signature) (*jvmti_env)->Deallocate(jvmti_env, (unsigned char *)method_signature);
    // if (constant_pool) (*jvmti_env)->Deallocate(jvmti_env, constant_pool);
}

//Callback method
void onVMInit(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread)
{
    printf("loading jar...\n");
    const char* javaNetUrlClassName = "java/net/URL";
    char* jarPath = (char*) "file:///Users/serena/Dottorato/KTH/classport-dev/classport/classport-commons/target/classport-commons-0.1.0-SNAPSHOT.jar";
    jclass urlClass = (*jni_env)->FindClass(jni_env, javaNetUrlClassName);
    if(urlClass == NULL){
        char msg[50];
        sprintf(msg, "couldn't find class: %s\n", javaNetUrlClassName);
        (*jni_env)->FatalError(jni_env,(char *) msg);
    }
    printf("urlClass created\n");

    jmethodID urlConstructor = (*jni_env)->GetMethodID(jni_env,urlClass, "<init>", "(Ljava/lang/String;)V");
    jstring jarPathJString = (*jni_env)->NewStringUTF(jni_env,jarPath);
    jobject url = (*jni_env)->NewObject(jni_env,urlClass, urlConstructor, jarPathJString);
    (*jni_env)->ReleaseStringUTFChars(jni_env,jarPathJString, NULL);
    if(url == NULL){
        (*jni_env)->FatalError(jni_env,"Couldn't create URL object");
    }
    printf("jar url created\n");

    jobjectArray urls = (*jni_env)->NewObjectArray(jni_env,1, (*jni_env)->FindClass(jni_env,javaNetUrlClassName), NULL);
    (*jni_env)->SetObjectArrayElement(jni_env,urls, 0, url);

    jclass classLoaderClass = (*jni_env)->FindClass(jni_env,"java/net/URLClassLoader");
    jmethodID constructor = (*jni_env)->GetMethodID(jni_env,classLoaderClass, "<init>", "([Ljava/net/URL;)V");
    jobject classLoader = (*jni_env)->NewObject(jni_env,classLoaderClass, constructor, urls);
    printf("classloader created \n");

    jclass clazz = (*jni_env)->FindClass(jni_env,"java/lang/Class");
    jmethodID classForNameMethod = (*jni_env)->GetStaticMethodID(jni_env,clazz, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
    if(classForNameMethod == NULL){
        (*jni_env)->FatalError(jni_env,"Class.forName(String,boolean,java.lang.ClassLoader)java.lang.Class method not found\n");
    }

    char *jarInitClass = "io.github.chains_project.classport.commons.ClassportInfo";
    jstring classToLoad = (*jni_env)->NewStringUTF(jni_env,jarInitClass);
    //bool t = true;
    jclass jarClass = (jclass) (*jni_env)->CallStaticObjectMethod(jni_env,clazz, classForNameMethod, classToLoad, 0, classLoader);
    (*jni_env)->ReleaseStringUTFChars(jni_env,classToLoad, NULL);
    if(jarClass == NULL){
        printf("jarClass found %s\n", jarClass);
        char msg[100];
        sprintf(msg, "jar init class not found: %s\n", jarInitClass);
        // (*jni_env)->FatalError(jni_env,(char *) msg);
    }
}

// Used for attaching at start-up time using the -agentpath option
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv *jvmti;
    jvmtiCapabilities      capabilities = {0};
    jvmtiError             error;
    jvmtiEventCallbacks    callbacks;

    // Get the JVMTI environment
    jint res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_2);
    if (res != JNI_OK) {
        printf("ERROR: Unable to access JVMTI\n");
        return JNI_ERR;
    }

    error = (*jvmti)->GetPotentialCapabilities(jvmti, &capabilities);
    if (error == JVMTI_ERROR_NONE) {
        (*jvmti)->AddCapabilities(jvmti, &capabilities);
    }

    (void)memset(&callbacks, 0, sizeof(callbacks));
    // Register the callback and enable the Method Entry event
    callbacks.MethodEntry = &onMethodEntry;
    callbacks.VMInit = (void*)&onVMInit;

    (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
    (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, NULL);
    (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT , NULL);

    printf("Agent_OnLoad\n");

    return JNI_OK;
}


// Used for attaching at run-time using the Attach API written in AgentLoader.java
JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
        jvmtiEnv *jvmti;
        jvmtiCapabilities      capabilities;
        jvmtiError             error;
        jvmtiEventCallbacks    callbacks;

        // Get the JVMTI environment
        jint res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_2);
        if (res != JNI_OK) {
            printf("ERROR: Unable to access JVMTI\n");
            return JNI_ERR;
        }

        error = (*jvmti)->GetPotentialCapabilities(jvmti, &capabilities);
        if (error == JVMTI_ERROR_NONE) {
            (*jvmti)->AddCapabilities(jvmti, &capabilities);
        }

        jint maxFrameCount = 10;
        jvmtiStackInfo *stack_info;
        jint thread_count;
        error = (*jvmti)->GetAllStackTraces(jvmti, maxFrameCount, &stack_info, &thread_count);
        if (error != JVMTI_ERROR_NONE) {
            printf("ERROR: Unable to get all stack traces\n");
            return JNI_ERR;
        }
        printf("%d threads\n", thread_count);
        for (int ti = 0; ti < thread_count; ++ti) {
           jvmtiStackInfo *infop = &stack_info[ti];
           jthread thread = infop->thread;
           jint state = infop->state;
           jvmtiFrameInfo *frames = infop->frame_buffer;

           jvmtiThreadInfo thread_info;
           error = (*jvmti)->GetThreadInfo(jvmti, thread, &thread_info);
           if (error != JVMTI_ERROR_NONE) {
               printf("ERROR: Unable to get thread info\n");
               return JNI_ERR;
           }
           else {
               printf("Thread: %s\n", thread_info.name);
           }

           for (int fi = 0; fi < infop->frame_count; fi++) {
                printf("Method %d\n", frames[fi].method);
                printf("Location %d\n", frames[fi].location);

                jlocation location = frames[fi].location;
                if (location != -1) {
                    char *method_name;
                    char *method_signature;
                    jclass declaring_class;
                    char *class_signature;
                    error = (*jvmti)->GetMethodName(jvmti, frames[fi].method, &method_name, &method_signature, NULL);
                    if (error != JVMTI_ERROR_NONE) {
                        printf("ERROR: Unable to get method name\n");
                        return JNI_ERR;
                    }
                    error = (*jvmti)->GetMethodDeclaringClass(jvmti, frames[fi].method, &declaring_class);
                    if (error != JVMTI_ERROR_NONE) {
                        printf("ERROR: Unable to get declaring class\n");
                        return JNI_ERR;
                    }
                    error = (*jvmti)->GetClassSignature(jvmti, declaring_class, &class_signature, NULL);
                    if (error != JVMTI_ERROR_NONE) {
                        printf("ERROR: Unable to get class signature\n");
                        return JNI_ERR;
                    }
                    printf("Method: %s%s\n", method_name, method_signature);
                    printf("Class: %s\n", class_signature);
                    if (method_name) (*jvmti)->Deallocate(jvmti, (unsigned char *)method_name);
                    if (method_signature) (*jvmti)->Deallocate(jvmti, (unsigned char *)method_signature);
                    if (class_signature) (*jvmti)->Deallocate(jvmti, (unsigned char *)class_signature);
                }
           }
        }

        return JNI_OK;
}


