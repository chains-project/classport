package io.github.project.classport.plugin;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import io.github.project.classport.commons.ClassportInfo;

class JarHelper {
    private final File source;
    private final File target;
    private final boolean overwrite;

    // All class files begin with the magic bytes 0xCAFEBABE
    // TODO: Refactor this class and put it in classport-commons
    // TODO: Expose this constant
    private static final byte[] magicBytes = new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE };

    public JarHelper(File source, File target) {
        this(source, target, false);
    }

    public JarHelper(File source, File target, boolean overwrite) {
        this.source = source;
        this.target = target;
        this.overwrite = overwrite;
    }

    // We handle MANIFEST.MF separately. See
    // https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File
    private boolean isSignatureFile(String filename) {
        return filename.startsWith("META-INF/SIG-") ||
                filename.startsWith("META-INF/")
                        && (filename.endsWith(".SF") || filename.endsWith(".RSA") || filename.endsWith(".DSA"));
    }

    // Slightly revised version of `buildUnsignedManifest` from Maven's JarSigner:
    // https://github.com/apache/maven-jarsigner/blob/9bc044710ff5c13bc540992a9a998453f213fd1b/src/main/java/org/apache/maven/shared/jarsigner/JarSignerUtil.java
    private Manifest getUnsignedManifest(Manifest m) {
        Manifest unsigned = new Manifest(m);
        unsigned.getEntries().clear();

        for (Map.Entry<String, Attributes> manifestEntry : m.getEntries().entrySet()) {
            Attributes oldAttributes = manifestEntry.getValue();
            Attributes newAttributes = new Attributes();

            for (Map.Entry<Object, Object> attributesEntry : oldAttributes.entrySet()) {
                String attributeKey = String.valueOf(attributesEntry.getKey());
                if (!attributeKey.endsWith("-Digest")) {
                    newAttributes.put(attributesEntry.getKey(), attributesEntry.getValue());
                }
            }

            // Only retain entries with non-digest attributes
            if (!newAttributes.isEmpty()) {
                unsigned.getEntries().put(manifestEntry.getKey(), newAttributes);
            }
        }

        return unsigned;
    }

    // Represents a processed JAR entry ready to be written
    private static class ProcessedEntry {
        final String relPath;
        final byte[] content;
        final boolean isDirectory;

        ProcessedEntry(String relPath, byte[] content, boolean isDirectory) {
            this.relPath = relPath;
            this.content = content;
            this.isDirectory = isDirectory;
        }
    }

    public void embed(ClassportInfo metadata) throws IOException {
        File tmpdir = Files.createTempDirectory("classport").toFile();
        extractTo(tmpdir);

        if (target.exists() && !overwrite)
            throw new IOException("File or directory " + target + " already exists. Skipping embed...");
        if (source.isDirectory())
            throw new IOException("Embedding metadata requires a jar as source. Skipping embed...");

        target.getParentFile().mkdirs();

        Path tmpdirPath = tmpdir.toPath();
        
        List<Path> files = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        
        Files.walkFileTree(tmpdirPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(tmpdirPath)) {
                    directories.add(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        Map<String, ProcessedEntry> processedEntries = new ConcurrentHashMap<>();
        
        files.parallelStream().forEach(file -> {
            try {
                String relPath = tmpdirPath.relativize(file).toString();
                
                // Signed JARs won't work since we edit the contents, so remove signatures
                if (isSignatureFile(relPath))
                    return;

                byte[] content;
                boolean isManifest = relPath.equals("META-INF/MANIFEST.MF");
                
                if (isManifest) {
                    try (InputStream in = Files.newInputStream(file)) {
                        Manifest manifest = new Manifest(in);
                        Manifest unsigned = getUnsignedManifest(manifest);
                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                        unsigned.write(buf);
                        content = buf.toByteArray();
                    }
                } else {
                    // We need to "peek" at the first 4 bytes to see if it's a classfile or not
                    try (InputStream in = Files.newInputStream(file)) {
                        byte[] header = in.readNBytes(4);
                        if (header.length != 4 || !Arrays.equals(header, magicBytes)) {
                            ByteArrayOutputStream buf = new ByteArrayOutputStream();
                            buf.write(header);
                            in.transferTo(buf);
                            content = buf.toByteArray();
                        } else {
                            // Class file - read all, add metadata
                            ByteArrayOutputStream buf = new ByteArrayOutputStream((int) Files.size(file));
                            buf.write(header);
                            in.transferTo(buf);
                            content = new MetadataAdder(buf.toByteArray()).add(metadata);
                        }
                    }
                }
                
                processedEntries.put(relPath, new ProcessedEntry(relPath, content, false));
            } catch (IOException e) {
                throw new RuntimeException("Failed to process file " + file, e);
            }
        });

        // Add directory entries
        for (Path dir : directories) {
            String relPath = tmpdirPath.relativize(dir).toString() + "/";
            processedEntries.put(relPath, new ProcessedEntry(relPath, null, true));
        }

        // Write entries to JAR in order (directories first, then files)
        try (JarOutputStream out = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(target)))) {
            // Write directories first
            processedEntries.values().stream()
                    .filter(e -> e.isDirectory)
                    .sorted((a, b) -> a.relPath.compareTo(b.relPath))
                    .forEach(entry -> {
                        try {
                            out.putNextEntry(new JarEntry(entry.relPath));
                            out.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to write directory entry " + entry.relPath, e);
                        }
                    });
            
            // Then write files
            processedEntries.values().stream()
                    .filter(e -> !e.isDirectory)
                    .sorted((a, b) -> a.relPath.compareTo(b.relPath))
                    .forEach(entry -> {
                        try {
                            out.putNextEntry(new JarEntry(entry.relPath));
                            out.write(entry.content);
                            out.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to write file entry " + entry.relPath, e);
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
