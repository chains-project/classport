package io.github.project.classport.plugin;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import io.github.project.classport.commons.AnnotationConstantPool;
import io.github.project.classport.commons.ClassportInfo;
import io.github.project.classport.commons.Utility;

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
        if (m == null) {
            return new Manifest();
        }
        
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
        if (target.exists() && !overwrite)
            throw new IOException("File or directory " + target + " already exists. Skipping embed...");
        if (source.isDirectory())
            throw new IOException("Embedding metadata requires a jar as source. Skipping embed...");
        
        target.getParentFile().mkdirs();
        
        Path inJar = source.toPath();
        Path outJar = target.toPath();
        
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(inJar));
             JarOutputStream jos = new JarOutputStream(
                 new BufferedOutputStream(Files.newOutputStream(outJar)), 
                 getUnsignedManifest(jis.getManifest()))) {
            
            JarEntry entry;
            byte[] buffer = new byte[8192];
            
            while ((entry = jis.getNextJarEntry()) != null) {
                // Skip signature files
                if (isSignatureFile(entry.getName())) {
                    continue;
                }
                
                // Skip MANIFEST.MF since it's already written via JarOutputStream constructor
                if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                    // Still need to read and discard the entry data
                    readEntryBytes(jis, buffer);
                    continue;
                }
                
                // Copy metadata
                JarEntry newEntry = new JarEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                jos.putNextEntry(newEntry);
                
                // Read the entire entry into memory
                byte[] entryBytes = readEntryBytes(jis, buffer);
                
                if (shouldReplace(entry, entryBytes)) {
                    MetadataAdder adder = new MetadataAdder(entryBytes);
                    jos.write(adder.add(metadata));
                } else {
                    // Copy entry as-is
                    jos.write(entryBytes);
                }
                
                jos.closeEntry();
            }
        }
    }
    
    private byte[] readEntryBytes(JarInputStream jis, byte[] buffer) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read;
        while ((read = jis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }
    
    private boolean shouldReplace(JarEntry entry, byte[] entryBytes) {
        // Check if it's a class file
        if (!entry.getName().endsWith(".class") || entryBytes.length < 4) {
            return false;
        }
        
        // Check magic bytes
        byte[] firstBytes = Arrays.copyOfRange(entryBytes, 0, 4);
        return Arrays.equals(firstBytes, Utility.MAGIC_BYTES);
    }
    
    private byte[] replacementBytes(byte[] classBytes, 
                                     AnnotationConstantPool acp, 
                                     AnnotationConstantPool.ConstantPoolData cpData) {
        return acp.injectAnnotation(classBytes, cpData);
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
