#include <jvmti.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>

static char jar_path[1024];

int setEventNotification(jvmtiEnv *jvmti, jvmtiEvent event_type, jvmtiEventMode mode, jthread thread) {
    jvmtiError error = (*jvmti)->SetEventNotificationMode(jvmti, mode, event_type, thread);
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to set event notification mode\n");
        return 0;
    }
    return 1;
}

// Callback for Method Entry
void JNICALL onMethodEntry(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread, jmethodID method) {
    char *method_name = NULL;
    char *method_signature = NULL;
    char *class_signature = NULL;
    jclass declaring_class;
    jvmtiError err;

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

    //  && strcmp(method_name, "customSleepingThread") == 0
    if (err == JVMTI_ERROR_NONE) {
        // printf("Executing customSleepingThread method\n");
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

        if (setEventNotification(jvmti_env, JVMTI_EVENT_METHOD_ENTRY, JVMTI_DISABLE, NULL) == 0) {
            fprintf(stderr, "Error: Unable to disable METHOD_ENTRY event in onMethodEntry\n");
            return;
        }
        jobject annotations = (*jni_env)->CallObjectMethod(jni_env, declaring_class, getAnnotations_method);
        if (annotations == NULL) {
            fprintf(stderr, "Error: Unable to get annotations in onMethodEntry\n");
            return;
        }
        if (setEventNotification(jvmti_env, JVMTI_EVENT_METHOD_ENTRY, JVMTI_ENABLE, NULL) == 0) {
            fprintf(stderr, "Error: Unable to enable METHOD_ENTRY event in onMethodEntry\n");
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

    // Deallocate JVMTI memory
    if (class_signature) (*jvmti_env)->Deallocate(jvmti_env, (unsigned char *)class_signature);
    if (method_name) (*jvmti_env)->Deallocate(jvmti_env, (unsigned char *)method_name);
    if (method_signature) (*jvmti_env)->Deallocate(jvmti_env, (unsigned char *)method_signature);
}

void onVMInit(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread)
{
    printf("loading jar...\n");
    (*jvmti_env)->AddToBootstrapClassLoaderSearch(jvmti_env, jar_path);
}



// Used for attaching at start-up time using the -agentpath option
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv *jvmti;
    jvmtiCapabilities      capabilities = {0};
    jvmtiError             error;
    jvmtiEventCallbacks    callbacks = {0};

    // Get the JVMTI environment
    jint res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_2);
    if (res != JNI_OK || jvmti == NULL) {
        printf("ERROR: Unable to access JVMTI\n");
        return JNI_ERR;
    }

    // Retrieve potential capabilities
    error = (*jvmti)->GetPotentialCapabilities(jvmti, &capabilities);
    if (error == JVMTI_ERROR_NONE) {
        error = (*jvmti)->AddCapabilities(jvmti, &capabilities);
        if (error != JVMTI_ERROR_NONE) {
            printf("ERROR: AddCapabilities failed: %d\n", error);
            return JNI_ERR;
        }
    } else {
        printf("ERROR: GetPotentialCapabilities failed: %d\n", error);
        return JNI_ERR;
    }

    // Register the callback and enable the Method Entry event
    callbacks.MethodEntry = &onMethodEntry;
    callbacks.VMInit = onVMInit;

    error = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: SetEventCallbacks failed: %d\n", error);
        return JNI_ERR;
    }

    // Enable JVMTI events
    if (setEventNotification(jvmti, JVMTI_EVENT_METHOD_ENTRY, JVMTI_ENABLE, NULL) == 0) {
        printf("ERROR: Enabling METHOD_ENTRY event failed: %d\n", error);
        return JNI_ERR;
    }
    // error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, NULL);
    // if (error != JVMTI_ERROR_NONE) {
    //     printf("ERROR: Enabling METHOD_ENTRY event failed: %d\n", error);
    //     return JNI_ERR;
    // }
    if (setEventNotification(jvmti, JVMTI_EVENT_VM_INIT, JVMTI_ENABLE, NULL) == 0) {
        printf("ERROR: Enabling VM_INIT event failed: %d\n", error);
        return JNI_ERR;
    }

    // error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
    // if (error != JVMTI_ERROR_NONE) {
    //     printf("ERROR: Enabling VM_INIT event failed: %d\n", error);
    //     return JNI_ERR;
    // }

    // Set the jar path
    if (options != NULL) {
        strncpy(jar_path, options, sizeof(jar_path) - 1);
        jar_path[sizeof(jar_path) - 1] = '\0'; // Ensure null-terminated
        printf("Agent options: %s\n", jar_path);
    } else {
        printf("The full path to classport-commons/target/classport-commons-0.1.0-SNAPSHOT.jar is needed. See doc.\n");
        return JNI_ERR;
    }

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


