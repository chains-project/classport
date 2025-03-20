package io.github.chains_project.classport.instrumentation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.chains_project.classport.commons.ClassportInfo;

public class MethodInterceptorVisitor extends ClassVisitor {
    private final String className;
    private final ClassportInfo ann;
    private static final int QUEUE_CAPACITY = 1000000;
    static final BlockingQueue<String> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    //private  final Thread writerThread;
    private static final int QUEUE_THRESHOLD = 100000;

    public MethodInterceptorVisitor(ClassVisitor cv, String className, ClassportInfo ann) {
        super(Opcodes.ASM9, cv);
        this.className = className;
        this.ann = ann;
    
         // Write header to the file if it doesn't exist or is empty
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.csv", true))) {
            File file = new File("output.csv");
            if (file.length() == 0) { // Check if the file is empty
                writer.write("Class,Method,sourceProjectId,isDirect,id,artefact,group,version,childIds\n"); // Replace with your actual headers
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize the queue processing thread
        Thread writerThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.csv", true))) {
                while (true) {
                    if (queue.size() >= QUEUE_THRESHOLD) {
                        synchronized (queue) {
                            while (!queue.isEmpty()) {
                                writer.write(queue.poll() + "\n");
                            }
                            writer.flush(); // Flush after processing the batch
                        }
                    }
                    Thread.sleep(100); // Avoid busy-waiting
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    
        writerThread.setDaemon(true);
        writerThread.start();
    
        // Add a shutdown hook to process remaining items in the queue
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.csv", true))) {
                synchronized (queue) {
                    while (!queue.isEmpty()) {
                        writer.write(queue.poll() + "\n");
                    }
                    writer.flush(); // Ensure all remaining data is written
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }
    
    public static void addToQueue(String content) {
        queue.offer(content);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodInterceptor(mv, name, className, ann);
    }
}

class MethodInterceptor extends MethodVisitor {
    private final String methodName;
    private final String className;
    private final ClassportInfo ann;

    public MethodInterceptor(MethodVisitor mv, String methodName, String className, ClassportInfo ann) {
        super(Opcodes.ASM9, mv);
        this.methodName = methodName;
        this.className = className;
        this.ann = ann;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        // Inject code to add to the queue every time the method is invoked
        mv.visitLdcInsn(className + "," + methodName + "," + ann.sourceProjectId()  + "," + ann.isDirectDependency() + "," + ann.id() + "," + ann.artefact() + "," + ann.group() + "," + ann.version() + "," + String.join(",", ann.childIds()));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                           "io/github/chains_project/classport/instrumentation/MethodInterceptorVisitor", 
                           "addToQueue", 
                           "(Ljava/lang/String;)V", 
                           false);
    }
}


