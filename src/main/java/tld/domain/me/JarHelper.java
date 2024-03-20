package tld.domain.me;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

class JarHelper {
    private final File source;
    private final File target;

    public JarHelper(File source, File target) {
        this.source = source;
        this.target = target;
    }

    public void embed(String prefix, HashMap<String, String> kvMetadata) throws IOException {
        File tmpdir = Files.createTempDirectory("classport").toFile();
        extractTo(tmpdir);

        if (target.exists())
            throw new IOException("File or directory " + target + " already exists");
        if (source.isDirectory())
            throw new IOException("Embedding metadata requires a jar as source");

        try (JarOutputStream os = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(target)))) {
            Files.walkFileTree(tmpdir.toPath(), new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relPath = tmpdir.toPath().relativize(file).toString();
                    // System.out.println("Found file: " + relPath);

                    // TODO Send the byte array into the metadataadder, get the new array from it,
                    // and write this one to disk

                    os.putNextEntry(new JarEntry(relPath));
                    try (FileInputStream in = new FileInputStream(file.toFile())) {
                        // TODO: In -> ASM -> Out instead of In -> Out
                        in.transferTo(os);
                    }
                    os.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String relPath = tmpdir.toPath().relativize(dir).toString() + "/";

                    // .jar files don't include the root ("/")
                    if (dir.equals(tmpdir.toPath()))
                        return FileVisitResult.CONTINUE;

                    System.out.println("Found directory: " + relPath);
                    os.putNextEntry(new JarEntry(relPath));
                    os.closeEntry();
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
