package cn.missdrop.datavault.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;

/** Relocates only private implementation types; MongoDB/Lettuce callback ABI stays unchanged. */
final class PrivateLibraryRelocator {
    Path prepare(RuntimeLibrary library, Path original) throws IOException {
        if (!library.path.startsWith("com/zaxxer/HikariCP/")) {
            return original;
        }
        Path target = original.resolveSibling(original.getFileName().toString().replace(".jar", "-datavault.jar"));
        Path temporary = Files.createTempFile(original.getParent(), "hikari-relocation-", ".part");
        try {
            // Escape source names so Shadow rewrites bytecode references, not these relocation rules.
            var rule = new Relocation("com{}zaxxer{}hikari".replace("{}", "."),
                    "cn.missdrop.datavault.libs.hikari");
            new JarRelocator(original.toFile(), temporary.toFile(), List.of(rule)).run();
            // Rebuild this small jar from the verified original each startup; never trust a stale transform.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
