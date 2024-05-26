package io.github.chains_project.classport.analyser;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ClassLoadingAdder {
    private static final String METHODNAME = "__classportForceClassLoading"; // TODO: Randomise?
    private static final String METHODDESCRIPTOR = "()V"; // void

    // @Override
    // public MethodVisitor visitMethod(int access, String name, String descriptor,
    // String signature,
    // String[] exceptions) {
    // return super.visitMethod(access, name, descriptor, signature, exceptions);
    // }

    /*
     * Adds a dummy method to a class file, containing instructions to load a bunch
     * of classes. The strings must be proper type descriptors such as
     * "Ljava/util/ArrayList;".
     *
     * TODO: Do we need to call this to make it not-dead code? Seemingly no
     */
    public static byte[] forceClassLoading(byte[] originalMainClass, String[] classes) {
        ClassReader cr = new ClassReader(originalMainClass);
        ClassWriter cw = new ClassWriter(cr, 0);
        ClassVisitor addMethodVisitor = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PRIVATE, METHODNAME, METHODDESCRIPTOR, null, null);
                if (mv != null) {
                    // Start of function
                    mv.visitCode();

                    // Bytecode
                    for (String c : classes) {
                        // Only try to load regular objects (no array types, primitive types etc.)
                        Type t = Type.getType("L" + c + ";");
                        mv.visitLdcInsn(t);
                        mv.visitVarInsn(Opcodes.ASTORE, 0);
                    }
                    mv.visitInsn(Opcodes.RETURN);

                    // End of function, we only use 1 var and 1 stack item
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                }

                super.visitEnd();
            }
        };

        cr.accept(addMethodVisitor, 0);
        return cw.toByteArray();
    }
}
