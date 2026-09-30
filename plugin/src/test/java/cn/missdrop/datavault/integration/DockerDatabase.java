package cn.missdrop.datavault.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Dedicated disposable containers: dynamic loopback ports, no shared database or volume. */
public final class DockerDatabase implements AutoCloseable {
    private final String name = "datavault-test-" + UUID.randomUUID();
    private final int port;

    public DockerDatabase(String image, int containerPort, String... environment) throws Exception {
        List<String> arguments = new ArrayList<>(Arrays.asList("docker", "run", "--detach", "--rm",
                "--name", name, "--label", "datavault.test=true", "-p", "127.0.0.1::" + containerPort));
        for (String value : environment) {
            arguments.add("-e");
            arguments.add(value);
        }
        arguments.add(image);
        try {
            command(arguments);
            String binding = command(Arrays.asList("docker", "port", name, containerPort + "/tcp")).trim();
            port = Integer.parseInt(binding.substring(binding.lastIndexOf(':') + 1));
        } catch (Exception failure) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** Host-side ephemeral port; fixtures never assume common development ports are free. */
    public int port() {
        return port;
    }

    /** Retry only startup, not test assertions, with a bounded deadline. */
    public <T> T await(Attempt<T> attempt) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                return attempt.run();
            } catch (Exception failure) {
                last = failure;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Container failed to become ready: " + name, last);
    }

    @Override
    public void close() throws Exception {
        command(Arrays.asList("docker", "rm", "--force", name));
    }

    private static String command(List<String> arguments) throws Exception {
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        // Docker output is short here; image pulls are performed separately before this task.
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Docker command timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException(output);
        }
        return output;
    }

    @FunctionalInterface
    public interface Attempt<T> {
        T run() throws Exception;
    }
}
