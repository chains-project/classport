package tld.domain.me.classport.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

class JarHelper {
    private final File source;
    private final File target;
    private final boolean overwrite;

    public JarHelper(File source, File target) {
        this(source, target, false);
    }

    public JarHelper(File source, File target, boolean overwrite) {
        this.source = source;
        this.target = target;
        this.overwrite = overwrite;
    }

    public void embed(HashMap<String, String> kvMetadata) throws IOException {
        File tmpdir = Files.createTempDirectory("classport").toFile();
        extractTo(tmpdir);

        if (target.exists() && !overwrite)
            throw new IOException("File or directory " + target + " already exists");
        if (source.isDirectory())
            throw new IOException("Embedding metadata requires a jar as source");

        target.getParentFile().mkdirs();

        try (JarOutputStream out = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(target)))) {
            Files.walkFileTree(tmpdir.toPath(), new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relPath = tmpdir.toPath().relativize(file).toString();

                    out.putNextEntry(new JarEntry(relPath));
                    // We need to "peek" at the first 4 bytes
                    try (PushbackInputStream in = new PushbackInputStream(new FileInputStream(file.toFile()), 4)) {
                        byte[] magicBytes = in.readNBytes(4);
                        in.unread(magicBytes);

                        // All class files begin with the magic bytes 0xCAFEBABE
                        if (Arrays.equals(magicBytes,
                                new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE })) {
                            MetadataAdder adder = new MetadataAdder(in.readAllBytes());
                            out.write(adder.add());
                        } else {
                            // Not a classfile, just stream the entire contents directly
                            in.transferTo(out);
                        }
                    }
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String relPath = tmpdir.toPath().relativize(dir).toString() + "/";

                    // .jar files don't include the root ("/")
                    if (dir.equals(tmpdir.toPath()))
                        return FileVisitResult.CONTINUE;

                    out.putNextEntry(new JarEntry(relPath));
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

            });
        }
    }

    public void extract() throws IOException {
        extractTo(this.target);
    }

    public void extractTo(File target) throws IOException {
        // sourcePath is a .jar, extract it to destDir
        try (JarFile jf = new JarFile(source)) {
            Enumeration<JarEntry> entries = jf.entries();

            if (entries != null) {
                if (!target.isDirectory()) {
                    if (!target.mkdir()) {
                        throw new IOException("Failed to create root directory at " + target);
                    }
                }

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    File entryFile = new File(target, entry.getName());
                    if (entry.isDirectory()) {
                        if (!entryFile.mkdir()) {
                            throw new IOException("Failed to create inner directory at " + entryFile);
                        }
                    } else {
                        try (InputStream in = jf.getInputStream(entry);
                                OutputStream out = new FileOutputStream(entryFile)) {
                            in.transferTo(out);
                        }
                    }
                }
            }
        }
    }
}
