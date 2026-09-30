package cn.missdrop.datavault.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

/** Rejects incomplete catalogs and paths that could escape the plugin's dependency cache. */
public class DependencyCatalogTest {
    @Test
    public void acceptsOnePinnedArtifactAndRejectsDuplicates() throws Exception {
        var library = MavenHttpFixture.library();
        String line = library.path + "\t" + library.size + "\t" + library.sha256 + "\n";
        assertEquals(1, DependencyCatalog.read(input(line)).size());
        assertThrows(IOException.class, () -> DependencyCatalog.read(input(line + line)));
    }

    @Test
    public void rejectsMissingMalformedOrEmptyCatalogs() {
        assertThrows(IOException.class, () -> DependencyCatalog.read(null));
        assertThrows(IOException.class, () -> DependencyCatalog.read(input("")));
        assertThrows(IOException.class, () -> DependencyCatalog.read(input("broken")));
    }

    @Test
    public void rejectsPathTraversalAndInvalidHashes() {
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeLibrary("../outside.jar", 1, "0".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeLibrary("org/example/../outside.jar", 1, "0".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeLibrary("org/example/library.jar", 1, "invalid"));
    }

    private static ByteArrayInputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
