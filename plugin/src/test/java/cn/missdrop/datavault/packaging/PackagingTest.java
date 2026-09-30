package cn.missdrop.datavault.packaging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.Test;
import static org.junit.Assert.*;

/** Verifies single-jar bootstrap, relocation and native ABI in the fixture's isolated plugin loader. */
public class PackagingTest {
    @Test
    public void bundlesOnlyOwnCodeAndRelocatedBootstrapHelpers() throws Exception {
        Path plugin = packagedJar();
        try (JarFile jar = new JarFile(plugin.toFile())) {
            assertNotNull(jar.getJarEntry("cn/missdrop/datavault/api/DataVault.class"));
            assertNotNull(jar.getJarEntry("plugin.yml"));
            assertNotNull(jar.getJarEntry("cn/missdrop/datavault/libs/libby/LibraryManager.class"));
            assertNull(jar.getJarEntry("cn/missdrop/datavault/libs/hikari/HikariConfig.class"));
            jar.stream().filter(entry -> entry.getName().endsWith(".class")).forEach(entry ->
                    assertTrue("Bundled third-party class: " + entry.getName(),
                            entry.getName().startsWith("cn/missdrop/datavault/")));
            String classPath = jar.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            assertNull("Dependencies must be injected after verification, not opened from a manifest", classPath);
            var catalog = jar.getJarEntry("META-INF/datavault/runtime-libraries.tsv");
            assertNotNull(catalog);
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(jar.getInputStream(catalog),
                    java.nio.charset.StandardCharsets.UTF_8))) {
                for (String line : (Iterable<String>) reader.lines()::iterator) {
                    String[] fields = line.split("\t");
                    assertEquals(3, fields.length);
                    assertTrue(fields[2].matches("[a-f0-9]{64}"));
                    Path library = plugin.getParent().resolve("DataVault/libraries").resolve(fields[0]);
                    assertEquals(Long.parseLong(fields[1]), Files.size(library));
                }
            }
        }
    }

    @Test
    public void nativeTypesSharePluginLoaderAndHikariIsPrivate() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        String[] classes = {"cn.missdrop.datavault.api.DataVault", "cn.missdrop.datavault.libs.hikari.HikariConfig",
                "org.sqlite.JDBC", "com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver",
                "org.postgresql.Driver", "org.h2.Driver", "org.duckdb.DuckDBDriver",
                "com.clickhouse.jdbc.Driver", "com.mongodb.client.MongoClients", "io.lettuce.core.RedisClient"};
        for (String name : classes) {
            Class<?> type = Class.forName(name, true, loader);
            assertSame("Dependency escaped the plugin loader: " + name, loader, type.getClassLoader());
        }
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.zaxxer.hikari.HikariConfig", false, loader));
    }

    @Test
    public void separateJarsPreserveEveryJdbcServiceDescriptor() {
        var drivers = new HashSet<String>();
        for (Driver driver : ServiceLoader.load(Driver.class, getClass().getClassLoader())) {
            drivers.add(driver.getClass().getName());
        }
        assertTrue(drivers.containsAll(java.util.Set.of("org.sqlite.JDBC", "com.mysql.cj.jdbc.Driver",
                "org.mariadb.jdbc.Driver", "org.postgresql.Driver", "org.h2.Driver", "org.duckdb.DuckDBDriver",
                "com.clickhouse.jdbc.Driver")));
    }

    /** Supplied only by packagedBackendTest; ordinary unit tests exclude this class. */
    private static Path packagedJar() {
        return Path.of(System.getProperty("datavault.packaged.jar"));
    }
}
