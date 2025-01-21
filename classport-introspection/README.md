We were trying `javaagent` instead of `jvmti` because we ended
up using the same APIs in JVMTI
1. getAllStackTraces
2. getAnnotations

However, upon trying to use `javaagent` we realized that we
were not able to get the `Class<?>` instance in the running JVM.
Thus, we can neither get its bytecode nor its annotations.

Some advantages of using `jvmti` are:
1. It uses `AsyncGetCallTrace` which is immune to [safepoint bias](https://seethawenner.medium.com/java-safepoint-and-async-profiling-cdce0818cd29) and does 
   not stop the JVM for long periods of time.
2. It gives us the `jclass` instance so we can call `getAnnotations` on it.