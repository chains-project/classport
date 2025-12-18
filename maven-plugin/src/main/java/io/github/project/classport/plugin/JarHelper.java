package io.github.project.classport.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import io.github.project.classport.commons.AnnotationConstantPool;
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
    
    public void embed(ClassportInfo metadata) throws IOException {
        AnnotationConstantPool acp = new AnnotationConstantPool(metadata);
        AnnotationConstantPool.ConstantPoolData cpData = acp.getNewEntries();
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
                        
                        // Signed JARs won't work since we edit the contents, so remove signatures
                        if (isSignatureFile(relPath))
                        return FileVisitResult.CONTINUE;

                    out.putNextEntry(new JarEntry(relPath));

                    // Remove signature attributes from manifest file
                    if (relPath.equals("META-INF/MANIFEST.MF")) {
                        Manifest manifest = new Manifest(new FileInputStream(file.toFile()));
                        Manifest unsigned = getUnsignedManifest(manifest);
                        unsigned.write(out);
                    } else {
                        // We need to "peek" at the first 4 bytes to see if it's a classfile or not
                        try (PushbackInputStream in = new PushbackInputStream(new FileInputStream(file.toFile()), 4)) {
                            byte[] firstBytes = in.readNBytes(4);
                            in.unread(firstBytes);

                            if (Arrays.equals(firstBytes, magicBytes)) {
                                // It's a classfile, embed the metadata
                                byte[] classBytes = in.readAllBytes();
                                byte[] modifiedCpBytes = acp.injectAnnotation(classBytes, cpData);

                                out.write(modifiedCpBytes);
                            } else {
                                // Not a classfile, just stream the entire contents directly
                                in.transferTo(out);
                            }
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
