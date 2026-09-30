package cn.missdrop.datavault.bootstrap;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/** Local Maven endpoint for deterministic download, corruption and offline-cache tests. */
final class MavenHttpFixture implements AutoCloseable {
    static final byte[] CONTENT = "driver".getBytes(StandardCharsets.UTF_8);
    final AtomicInteger requests = new AtomicInteger();
    final HttpServer server;
    final URI repository;

    MavenHttpFixture(int status, byte[] content) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(status, content.length);
            try (var output = exchange.getResponseBody()) {
                output.write(content);
            }
        });
        server.start();
        repository = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    static RuntimeLibrary library() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(CONTENT);
        var checksum = new StringBuilder();
        for (byte value : digest) {
            checksum.append(String.format("%02x", value & 255));
        }
        return new RuntimeLibrary("org/example/driver/1.0/driver-1.0.jar", CONTENT.length, checksum.toString());
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
