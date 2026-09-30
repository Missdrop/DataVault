package cn.missdrop.datavault.bootstrap;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/** Validates cached jars and publishes downloads only after size and checksum verification. */
final class MavenLibraryCache {
    private final Path root;
    private final URI repository;
    private final Logger logger;
    private final MavenDownload transport = new MavenDownload();

    MavenLibraryCache(Path root, URI repository, Logger logger) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
        this.repository = repository;
        this.logger = logger;
    }

    synchronized Path ensure(RuntimeLibrary library) throws IOException {
        Path target = root.resolve(library.path).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Library path escapes cache: " + library.path);
        }
        Files.createDirectories(target.getParent());
        // Prevent existing directory symlinks from redirecting writes outside this plugin's cache.
        if (!target.getParent().toRealPath().startsWith(root)) {
            throw new IOException("Library directory escapes cache: " + library.path);
        }
        if (LibraryChecksum.matches(target, library)) {
            return target;
        }
        logger.info("Downloading Maven dependency " + library.path);
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            transport.fetch(repository.resolve(library.path), temporary, library.size);
            if (!LibraryChecksum.matches(temporary, library)) {
                throw new IOException("SHA-256 mismatch for " + library.path);
            }
            publish(temporary, target);
            return target;
        } catch (IOException failure) {
            throw new IOException("Cannot install Maven dependency " + library.path, failure);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Interrupted downloads never overwrite a previous cache entry with a partial file. */
    private static void publish(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException failure) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
