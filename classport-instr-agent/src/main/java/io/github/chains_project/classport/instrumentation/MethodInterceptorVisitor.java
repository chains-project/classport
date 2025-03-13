package io.github.chains_project.classport.instrumentation;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class MethodInterceptorVisitor extends ClassVisitor {
    private final String className;

    public MethodInterceptorVisitor(ClassVisitor cv, String className) {
        super(Opcodes.ASM9, cv);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodInterceptor(mv, name, className);
    }
}

class MethodInterceptor extends MethodVisitor {
    private final String methodName;
    private final String className;

    public MethodInterceptor(MethodVisitor mv, String methodName, String className) {
        super(Opcodes.ASM9, mv);
        this.methodName = methodName;
        this.className = className;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("Entering method: " + methodName + " in class: " + className);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        
    }
}


