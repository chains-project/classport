package io.github.project.classport.analyser;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

class ClassInfo {
    String name;
    int access;

    public ClassInfo(String name, int access) {
        this.name = name;
        this.access = access;
    };
}

class ClassNameExtractor {
    public static ClassInfo getInfo(byte[] classFileBytes) {
        ClassReader classReader = new ClassReader(classFileBytes);
        GetClassNameVisitor classVisitor = new GetClassNameVisitor(Opcodes.ASM9);
        classReader.accept(classVisitor, 0);

        return new ClassInfo(classVisitor.getName(),
                classVisitor.getAccess());
    }
}

class GetClassNameVisitor extends ClassVisitor {
    private String className;
    private int access;

    public GetClassNameVisitor(int version) {
        super(version);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        className = name;
        this.access = access;
        // super.visit(version, access, name, signature, superName, interfaces);
    }

    public String getName() {
        return className;
    }

    public int getAccess() {
        return access;
    }
}
