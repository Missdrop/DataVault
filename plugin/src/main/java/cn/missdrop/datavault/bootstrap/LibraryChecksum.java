package cn.missdrop.datavault.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Streams integrity checks without allocating native-driver-sized byte arrays. */
final class LibraryChecksum {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private LibraryChecksum() { }

    static boolean matches(Path file, RuntimeLibrary library) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != library.size) {
            return false;
        }
        return library.sha256.equals(digest(file));
    }

    private static String digest(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            var hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(HEX[(value & 255) >>> 4]).append(HEX[value & 15]);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError("SHA-256 unavailable", failure);
        }
    }
}
