package cn.missdrop.datavault.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Checks cache repair and fail-closed behavior without contacting public repositories. */
public class MavenLibraryCacheTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void downloadsOnceAndValidCacheWorksOffline() throws Exception {
        RuntimeLibrary library = MavenHttpFixture.library();
        Path downloaded;
        try (var endpoint = new MavenHttpFixture(200, MavenHttpFixture.CONTENT)) {
            var cache = cache(endpoint);
            downloaded = cache.ensure(library);
            cache.ensure(library);
            assertEquals(1, endpoint.requests.get());
        }
        // A closed local port proves the cache path performs no network request.
        var offline = new MavenLibraryCache(temporary.getRoot().toPath(),
                java.net.URI.create("http://127.0.0.1:1/"), Logger.getAnonymousLogger());
        assertEquals(downloaded, offline.ensure(library));
    }

    @Test
    public void repairsSameSizeCorruptedCache() throws Exception {
        try (var endpoint = new MavenHttpFixture(200, MavenHttpFixture.CONTENT)) {
            var cache = cache(endpoint);
            RuntimeLibrary library = MavenHttpFixture.library();
            Path file = cache.ensure(library);
            Files.write(file, "broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            cache.ensure(library);
            assertEquals(2, endpoint.requests.get());
            assertTrue(LibraryChecksum.matches(file, library));
        }
    }

    @Test
    public void invalidDownloadDoesNotPublishOrOverwriteExistingEntry() throws Exception {
        try (var endpoint = new MavenHttpFixture(200, "broken".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            RuntimeLibrary library = MavenHttpFixture.library();
            Path target = temporary.getRoot().toPath().resolve(library.path);
            Files.createDirectories(target.getParent());
            Files.write(target, MavenHttpFixture.CONTENT);
            // Deliberately use an impossible digest so both cached and downloaded bytes are untrusted.
            var invalid = new RuntimeLibrary(library.path, library.size, "0".repeat(64));
            assertThrows(IOException.class, () -> cache(endpoint).ensure(invalid));
            assertArrayEquals(MavenHttpFixture.CONTENT, Files.readAllBytes(target));
            assertNoPartFiles(target.getParent());
        }
    }

    @Test
    public void httpFailureLeavesNoPublishedJarOrPartialFile() throws Exception {
        try (var endpoint = new MavenHttpFixture(404, MavenHttpFixture.CONTENT)) {
            RuntimeLibrary library = MavenHttpFixture.library();
            assertThrows(IOException.class, () -> cache(endpoint).ensure(library));
            Path target = temporary.getRoot().toPath().resolve(library.path);
            assertFalse(Files.exists(target));
            assertNoPartFiles(target.getParent());
        }
    }

    private MavenLibraryCache cache(MavenHttpFixture endpoint) throws IOException {
        return new MavenLibraryCache(temporary.getRoot().toPath(), endpoint.repository, Logger.getAnonymousLogger());
    }

    private static void assertNoPartFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(file -> file.toString().endsWith(".part")));
        }
    }
}
