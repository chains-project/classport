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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import tld.domain.me.classport.commons.ClassportInfo;

class JarHelper {
    private final File source;
    private final File target;
    private final boolean overwrite;

    // All class files begin with the magic bytes 0xCAFEBABE
    private static final byte[] magicBytes = new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };

    public JarHelper(File source, File target) {
        this(source, target, false);
    }

    public JarHelper(File source, File target, boolean overwrite) {
        this.source = source;
        this.target = target;
        this.overwrite = overwrite;
    }

    public void embed(ClassportInfo metadata) throws IOException {
        File tmpdir = Files.createTempDirectory("classport").toFile();
        extractTo(tmpdir);

        if (target.exists() && !overwrite)
            throw new IOException("File or directory " + target + " already exists. Skipping embed...");
        if (source.isDirectory())
            throw new IOException("Embedding metadata requires a jar as source. Skipping embed...");

        target.getParentFile().mkdirs();

        try (JarOutputStream out = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(target)))) {
            Files.walkFileTree(tmpdir.toPath(), new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relPath = tmpdir.toPath().relativize(file).toString();

                    out.putNextEntry(new JarEntry(relPath));
                    // We need to "peek" at the first 4 bytes
                    try (PushbackInputStream in = new PushbackInputStream(new FileInputStream(file.toFile()), 4)) {
                        byte[] firstBytes = in.readNBytes(4);
                        in.unread(firstBytes);

                        if (Arrays.equals(firstBytes, magicBytes)) {
                            MetadataAdder adder = new MetadataAdder(in.readAllBytes());
                            out.write(adder.add(metadata));
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
        try (JarFile jf = new JarFile(source)) {
            Enumeration<JarEntry> entries = jf.entries();

            if (entries != null) {
                if (!target.isDirectory()) {
                    if (!target.mkdirs()) {
                        throw new IOException("Failed to create root directory at " + target);
                    }
                }

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    File entryFile = new File(target, entry.getName());

                    // The entry's parent may not exist,, as .jar/.zip files may contain nested
                    // directories without the "middle" ones (i.e. `a/b/c` can exist without `a/b`)
                    if (!entryFile.getParentFile().isDirectory())
                        entryFile.getParentFile().mkdirs();

                    // Invariant: We always have a directory for this entry to be stored in
                    if (entry.isDirectory()) {
                        // Short-circuit if trying to create a directory that already exists
                        // Required since `a/b.ext` might come before `a` in the .jar/.zip
                        if (!entryFile.exists() && !entryFile.mkdir())
                            throw new IOException("Failed to create inner directory at " + entryFile);
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
