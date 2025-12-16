package io.github.project.classport.commons;

public class Utility {
    private Utility() {
        throw new IllegalStateException("Utility class");
    }

    // All class files begin with the magic bytes 0xCAFEBABE
    public static final byte[] MAGIC_BYTES = new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };
}
