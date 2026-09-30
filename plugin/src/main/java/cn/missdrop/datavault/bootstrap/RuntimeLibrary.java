package cn.missdrop.datavault.bootstrap;

/** Immutable build-locked Maven artifact; no version negotiation occurs on the server. */
final class RuntimeLibrary {
    final String path;
    final long size;
    final String sha256;

    RuntimeLibrary(String path, long size, String sha256) {
        if (!path.matches("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)+\\.jar")
                || java.util.Arrays.stream(path.split("/")).anyMatch(part -> part.equals("..") || part.equals("."))
                || size <= 0 || !sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid locked Maven artifact: " + path);
        }
        this.path = path;
        this.size = size;
        this.sha256 = sha256;
    }
}
