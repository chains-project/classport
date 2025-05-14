package io.github.chains_project.classport.instrumentation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.github.chains_project.classport.commons.ClassportInfo;

public class MethodInterceptorVisitor extends ClassVisitor {
    private static final int QUEUE_CAPACITY = 1000000;
    private static final int QUEUE_THRESHOLD = 100000;
    // static String OUTPUT_FILE = "output.csv";
    static String OUTPUT_FILE;
    static Path outputPath;

    private final String className;
    private final ClassportInfo ann; 
    static final BlockingQueue<String> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);    

    public MethodInterceptorVisitor(ClassVisitor cv, String className, ClassportInfo ann, String OUTPUT_FILE, Path OUTPUT_PATH_DIR) {
        super(Opcodes.ASM9, cv);
        this.className = className;
        this.ann = ann;
        MethodInterceptorVisitor.OUTPUT_FILE = OUTPUT_FILE;
    
        outputPath = OUTPUT_PATH_DIR.resolve(MethodInterceptorVisitor.OUTPUT_FILE);
        // Ensure the directory exists
        try {
            Files.createDirectories(OUTPUT_PATH_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + OUTPUT_PATH_DIR, e);
        }
        // Initialize the CSV file adding the header
        initializeCSVFileHeader();
        // Initialize the queue processing thread to write to the file in batches 
        initializeQueueWriterThread();
        // Add a shutdown hook to process remaining items in the queue
        addShutdownHookForQueueProcessing(OUTPUT_PATH_DIR);
    }

    private void addShutdownHookForQueueProcessing(Path OUTPUT_PATH_DIR) {
        // Add a shutdown hook to process remaining items in the queue
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            writeRemainingQueueToFile();
            writeNonAnnotatedClassesToFile(OUTPUT_PATH_DIR);
        }));
    }

    void writeRemainingQueueToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
            synchronized (queue) {
                while (!queue.isEmpty()) {
                    writer.write(queue.poll() + "\n");
                }
                writer.flush(); // Ensure all remaining data is written
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void writeNonAnnotatedClassesToFile(Path OUTPUT_PATH_DIR) {
        String output_file_name = OUTPUT_FILE.replace(".csv", "_nonAnnotatedClasses.txt");
        Path outputFilePath = OUTPUT_PATH_DIR.resolve(output_file_name);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath.toFile()))) {
            for (String nonAnnotatedClasseName : Agent.nonAnnotatedClasses) {
                writer.write(nonAnnotatedClasseName);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to write non-annotated classes to file: " + e.getMessage());
        }
    }

    private void initializeQueueWriterThread() {
        // Initialize the queue processing thread
        Thread writerThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
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
    }

    private void initializeCSVFileHeader() {
        // Write header to the file if it doesn't exist or is empty
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true))) {
            File file = outputPath.toFile();
            if (file.length() == 0) { 
                writer.write("Class,Method,sourceProjectId,isDirect,id,artefact,group,version,childIds\n"); 
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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


