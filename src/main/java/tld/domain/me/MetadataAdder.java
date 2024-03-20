package tld.domain.me;

import java.io.FileOutputStream;
import java.io.IOException;
import org.objectweb.asm.*;

/*
 * From JVMS Ch. 4.7 on class file attributes:
 * 
 * All attributes have the following general format:
 * ```
 * attribute_info {
 *   u2 attribute_name_index;
 *   u4 attribute_length;
 *   u1 info[attribute_length];
 * }
 * ```
 * [...]
 * The value of the attribute_length item indicates the length of the
 * subsequent information in bytes. The length does not include the initial
 * six bytes that contain the attribute_name_index and attribute_length
 * items.
 */

class MavenPackageAttribute extends Attribute {
    private String value;

    // Create a named custom attribute
    MavenPackageAttribute(String value) {
        super("Maven Package Name");
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    protected Attribute read(ClassReader cr, int off, int len,
            char[] buf, int codeOff, Label[] labels) {
        return new MavenPackageAttribute(cr.readUTF8(off, buf));
    }

    @Override
    protected ByteVector write(ClassWriter cw, byte[] code, int len,
            int maxStack, int maxLocals) {
        return new ByteVector().putShort(cw.newUTF8(value));
    }
}

class MavenSHAAttribute extends Attribute {
    private String value;

    // Create a named custom attribute
    MavenSHAAttribute(String value) {
        super("Maven jar SHA1");
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    protected Attribute read(ClassReader cr, int off, int len,
            char[] buf, int codeOff, Label[] labels) {
        return new MavenSHAAttribute(cr.readUTF8(off, buf));
    }

    @Override
    protected ByteVector write(ClassWriter cw, byte[] code, int len,
            int maxStack, int maxLocals) {
        return new ByteVector().putShort(cw.newUTF8(value));
    }
}

/*
 * A ClassVisitor can be seen as an event filter (ASM manual, p. 14)
 * Methods must be called in the following order:
 * visit visitSource? visitOuterClass? ( visitAnnotation | visitAttribute )*
 * ( visitInnerClass | visitField | visitMethod )*
 * visitEnd
 */
class AttributeVisitor extends ClassVisitor {
    /*
     * We pass in a ClassWriter as the visitor here (it's a subclass of it that
     * "serialises"/writes whatever it comes across). Any calls not overridden
     * will just be delegated to `cv` instead.
     *
     * This effectively means (or can be thought of as) that each part of the
     * class file that we encounter is just "forwarded" to the writer (using
     * copy optimisations, if I understand this correctly) except for the end
     * of the class file, where we insert (a) new attribute(s) before
     * signalling to the writer that we've reached the end.
     */
    public AttributeVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    /*
     * Write (a) new attribute(s) to the resulting class file before exiting.
     */
    @Override
    public void visitEnd() {
        cv.visitAttribute(new MavenPackageAttribute("a.very.good.package"));
        cv.visitAttribute(new MavenSHAAttribute("fake1337hash"));
        cv.visitEnd();
    }
}

public class MetadataAdder {
    ClassReader reader;
    ClassWriter writer;

    public MetadataAdder(byte[] bytes) throws IOException {
        reader = new ClassReader(bytes);
        /*
         * Keep a reference to the reader to enable copying optimisations (see
         * ASM manual, pp. 20-21). Should not be done when removing/renaming
         * stuff as it can result in things not being removed properly, but
         * it's fine when just used for adding.
         */
        writer = new ClassWriter(reader, 0);
    }

    public byte[] add() {
        System.out.println("Attempting to add attribute...");
        reader.accept(new AttributeVisitor(writer), 0);
        return writer.toByteArray();
    }
}
