package cn.missdrop.datavault.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Reads the complete, checksummed transitive closure embedded by the Gradle build. */
final class DependencyCatalog {
    static final String RESOURCE = "META-INF/datavault/runtime-libraries.tsv";

    private DependencyCatalog() { }

    static List<RuntimeLibrary> read(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("Missing bundled runtime dependency catalog");
        }
        var libraries = new ArrayList<RuntimeLibrary>();
        var paths = new HashSet<String>();
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", -1);
                if (columns.length != 3 || !paths.add(columns[0])) {
                    throw new IOException("Malformed or duplicate dependency catalog entry");
                }
                try {
                    libraries.add(new RuntimeLibrary(columns[0], Long.parseLong(columns[1]), columns[2]));
                } catch (IllegalArgumentException failure) {
                    throw new IOException("Invalid dependency catalog entry", failure);
                }
            }
        }
        if (libraries.isEmpty()) {
            throw new IOException("Empty runtime dependency catalog");
        }
        return List.copyOf(libraries);
    }
}
