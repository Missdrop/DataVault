package cn.missdrop.datavault.packaging;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import org.junit.Test;
import static org.junit.Assert.*;

/** Verifies the installation layout without inheriting drivers from the test classpath. */
public class PackagingTest {
    @Test
    public void containsOwnCodeOnlyAndReferencesExistingLibraries() throws Exception {
        Path plugin = packagedJar();
        try (JarFile jar = new JarFile(plugin.toFile())) {
            assertNotNull(jar.getJarEntry("cn/missdrop/datavault/api/DataVault.class"));
            assertNotNull(jar.getJarEntry("plugin.yml"));
            jar.stream().filter(entry -> entry.getName().endsWith(".class")).forEach(entry ->
                    assertTrue("Bundled third-party class: " + entry.getName(),
                            entry.getName().startsWith("cn/missdrop/datavault/")));
            String classPath = jar.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            assertNotNull("Missing external library manifest", classPath);
            for (String entry : classPath.split(" ")) {
                assertTrue(entry.startsWith("DataVault-libraries/"));
                assertTrue("Missing library: " + entry, Files.isRegularFile(
                        Path.of(plugin.toUri().resolve(entry))));
            }
        }
    }

    @Test
    public void installationZipContainsThePluginAndEveryReferencedLibrary() throws Exception {
        Path plugin = packagedJar();
        try (JarFile jar = new JarFile(plugin.toFile());
                ZipFile distribution = new ZipFile(System.getProperty("datavault.packaged.zip"))) {
            assertEquals(Files.size(plugin), distribution.getEntry(plugin.getFileName().toString()).getSize());
            assertNotNull(distribution.getEntry("INSTALL.txt"));
            String classPath = jar.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            for (String entry : classPath.split(" ")) {
                assertNotNull("Library missing from installation ZIP: " + entry, distribution.getEntry(entry));
                assertEquals(Files.size(Path.of(plugin.toUri().resolve(entry))),
                        distribution.getEntry(entry).getSize());
            }
        }
    }

    @Test
    public void isolatedLoaderFindsApiAndAllNativeClientsThroughManifest() throws Exception {
        // Platform parent supplies java.sql but cannot supply any Gradle/test dependencies.
        try (URLClassLoader loader = new URLClassLoader(new URL[]{packagedJar().toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            String[] classes = {"cn.missdrop.datavault.api.DataVault", "com.zaxxer.hikari.HikariConfig",
                    "org.sqlite.JDBC", "com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver",
                    "org.postgresql.Driver", "org.h2.Driver", "org.duckdb.DuckDBDriver",
                    "com.clickhouse.jdbc.Driver", "com.mongodb.client.MongoClients", "io.lettuce.core.RedisClient"};
            for (String name : classes) {
                Class<?> type = Class.forName(name, true, loader);
                assertSame("Dependency escaped the plugin loader: " + name, loader, type.getClassLoader());
            }
            assertNotNull(loader.getResource("META-INF/services/java.sql.Driver"));
        }
    }

    /** Supplied only by packagedBackendTest; ordinary unit tests exclude this class. */
    private static Path packagedJar() {
        return Path.of(System.getProperty("datavault.packaged.jar"));
    }
}
