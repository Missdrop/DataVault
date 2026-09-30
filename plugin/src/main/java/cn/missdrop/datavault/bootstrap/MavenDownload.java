package cn.missdrop.datavault.bootstrap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Bounded HTTP transport; redirects are rejected rather than trusting another host. */
final class MavenDownload {
    void fetch(URI source, Path temporary, long expectedSize) throws IOException {
        var connection = (HttpURLConnection) source.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "DataVault dependency bootstrap");
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Maven returned HTTP " + connection.getResponseCode() + " for " + source);
            }
            long declaredSize = connection.getContentLengthLong();
            if (declaredSize >= 0 && declaredSize != expectedSize) {
                throw new IOException("Unexpected Maven artifact size: " + source);
            }
            try (var input = connection.getInputStream(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > expectedSize || System.nanoTime() - deadline >= 0) {
                        throw new IOException("Artifact exceeds size or download deadline: " + source);
                    }
                    output.write(buffer, 0, count);
                }
                if (total != expectedSize) {
                    throw new IOException("Incomplete Maven artifact: " + source);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
