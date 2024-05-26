package io.github.chains_project.classport.analyser;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class ClassNameExtractor {
    public static String getName(byte[] classFileBytes) {
        ClassReader classReader = new ClassReader(classFileBytes);
        GetClassNameVisitor classVisitor = new GetClassNameVisitor(Opcodes.ASM9);
        classReader.accept(classVisitor, 0);

        return classVisitor.getName();
    }
}

class GetClassNameVisitor extends ClassVisitor {
    private String className;

    public GetClassNameVisitor(int version) {
        super(version);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public String getName() {
        return className;
    }
}
