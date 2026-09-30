package cn.missdrop.datavault.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.*;

/** Locks Gradle's resolved transitive artifacts so servers never resolve untrusted POMs. */
@CacheableTask
public abstract class RuntimeCatalogTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getArtifacts();

    @Input
    public abstract MapProperty<String, String> getMavenPaths();

    @OutputFile
    public abstract RegularFileProperty getCatalogFile();

    @TaskAction
    public void generate() throws IOException {
        var lines = new ArrayList<String>();
        for (var file : getArtifacts()) {
            String path = getMavenPaths().get().get(file.getName());
            if (path == null) {
                throw new IOException("No Maven coordinate for " + file.getName());
            }
            lines.add(path + "\t" + file.length() + "\t" + checksum(file.toPath()));
        }
        lines.sort(Comparator.naturalOrder());
        var output = getCatalogFile().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    /** SHA-256 is mandatory on the build JVM; stream large native jars instead of buffering them. */
    private static String checksum(java.nio.file.Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError("SHA-256 unavailable", failure);
        }
    }
}
