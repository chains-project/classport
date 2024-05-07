package tld.domain.me.classport.plugin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import tld.domain.me.classport.commons.ClassportInfo;

import org.objectweb.asm.*;

/**
 * A class for adding metadata to class files.
 *
 * We override quite a few methods here, but this is for good reasons:
 * 
 * 1. Annotations are ignored by the JVM if the classfile is below version 1.5
 * (also called version 5). We thus override the main visit method to provide a
 * way of upgrading this version to 1.5 - the minimum supported one. Note that
 * v1.5 was released in September 2004, so we most likely won't run into this.
 * See https://docs.oracle.com/javase/specs/jvms/se19/html/jvms-4.html#jvms-4.1
 *
 * 2. We are not allowed to just call "visitAnnotation" from the `visit`
 * function and then `super.visit`, as this could lead to annotations being
 * visited before things such as outerClass which is not allowed.
 *
 * 3. We want to make sure we don't add any duplicate annotations, so instead of
 * just adding the annotation unconditionally in `visitAnnotation`, we go
 * through all current annotations and take note of whether ours already exists.
 * Then we override all methods that may be called _after_ visitAnnotation and,
 * unless already added, add it here. This ensures that our annotation is added
 * at the correct stage while avoiding accidental duplication.
 *
 * Thanks to the ASM Manual (pp. 74-75) for providing this example.
 */
class AnnotationAdder extends ClassVisitor {
    private ClassportInfo annotationInstance;
    private boolean isAnnotationPresent;

    public AnnotationAdder(ClassVisitor cv, ClassportInfo annotationInstance) {
        super(Opcodes.ASM9, cv);
        this.annotationInstance = annotationInstance;
    }

    // TODO: Error here by default but provide an "automatic upgrade" option, unless
    // we can "prove" that upgrading is non-destructive. If so, default to upgrading
    @Override
    public void visit(int version, int access, String name,
            String signature, String superName, String[] interfaces) {
        int v = (version & 0xFF) < Opcodes.V1_5 ? Opcodes.V1_5 : version;
        cv.visit(v, access, name, signature, superName, interfaces);
    }

    /**
     * Figure out whether the annotation has already been added.
     */
    @Override
    public AnnotationVisitor visitAnnotation(String type,
            boolean visible) {
        if (visible && type.equals(annotationInstance.annotationType().getName())) {
            isAnnotationPresent = true;
        }
        return cv.visitAnnotation(type, visible);
    }

    /**
     * If we're supposed to add an annotation and it hasn't already been done, do it
     * before visiting the inner class
     */
    @Override
    public void visitInnerClass(String name, String outerName,
            String innerName, int access) {
        addAnnotation();
        cv.visitInnerClass(name, outerName, innerName, access);
    }

    /**
     * If we're supposed to add an annotation and it hasn't already been done, do it
     * before visiting fields.
     */
    @Override
    public FieldVisitor visitField(int access, String name, String desc,
            String signature, Object value) {
        addAnnotation();
        return cv.visitField(access, name, desc, signature, value);
    }

    /**
     * If we're supposed to add an annotation and it hasn't already been done, do it
     * before visiting methods.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name,
            String desc, String signature, String[] exceptions) {
        addAnnotation();
        return cv.visitMethod(access, name, desc, signature, exceptions);
    }

    /**
     * If we're supposed to add an annotation and it hasn't already been done, do it
     * before finishing up.
     */
    @Override
    public void visitEnd() {
        addAnnotation();
        cv.visitEnd();
    }

    private void addAnnotation() {
        if (!isAnnotationPresent) {
            String internalName = annotationInstance.annotationType().descriptorString();
            AnnotationVisitor av = cv.visitAnnotation(internalName, true);
            if (av != null) {
                try {
                    for (Method m : annotationInstance.annotationType().getDeclaredMethods()) {
                        String valueName = m.getName();
                        var val = m.invoke(annotationInstance);
                        if (val instanceof String[]) {
                            String[] elems = (String[]) val;
                            AnnotationVisitor arrayVisitor = av.visitArray(valueName);
                            for (String elem : elems)
                                arrayVisitor.visit(null, elem);
                            arrayVisitor.visitEnd();
                        } else
                            av.visit(valueName, val);
                    }
                    av.visitEnd();
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    System.err.println("Error adding metadata: " + e);
                }
            }
            isAnnotationPresent = true;
        }
    }
}

public class MetadataAdder {
    ClassReader reader;
    ClassWriter writer;
    Class<?> annotationClass;

    public MetadataAdder(byte[] bytes) throws IOException {
        this.annotationClass = ClassportInfo.class;
        reader = new ClassReader(bytes);
        /*
         * Keep a reference to the reader to enable copying optimisations (see
         * ASM manual, pp. 20-21). Should not be done when removing/renaming
         * stuff as it can result in things not being removed properly, but
         * it's fine when just used for adding.
         */
        writer = new ClassWriter(reader, 0);
    }

    public byte[] add(ClassportInfo metadata) throws IllegalArgumentException {
        reader.accept(new AnnotationAdder(writer, metadata), 0);
        return writer.toByteArray();
    }
}
