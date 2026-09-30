package cn.missdrop.datavault.bootstrap;

import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/** Downloads verified Maven artifacts, then attaches them before database runtime initialization. */
public final class DependencyBootstrap {
    private static final URI MAVEN_CENTRAL = URI.create("https://repo.maven.apache.org/maven2/");

    private DependencyBootstrap() { }

    /** Called during Bukkit onLoad, before DataVault or dependent plugins use native API types. */
    public static void install(Path pluginJar, Logger logger, ClassLoader pluginLoader) throws IOException {
        if (!(pluginLoader instanceof URLClassLoader)) {
            throw new IOException("Unsupported Bukkit class loader: " + pluginLoader.getClass().getName());
        }
        Path plugin = pluginJar.toAbsolutePath().normalize();
        try (var jar = new JarFile(plugin.toFile())) {
            var entry = jar.getJarEntry(DependencyCatalog.RESOURCE);
            if (entry == null) {
                throw new IOException("Plugin JAR has no runtime dependency catalog");
            }
            var libraries = DependencyCatalog.read(jar.getInputStream(entry));
            var cache = new MavenLibraryCache(plugin.getParent().resolve("DataVault/libraries"), MAVEN_CENTRAL, logger);
            var classpath = new PluginClasspath((URLClassLoader) pluginLoader,
                    plugin.getParent().resolve("DataVault"), logger);
            var relocator = new PrivateLibraryRelocator();
            for (var library : libraries) {
                classpath.addToClasspath(relocator.prepare(library, cache.ensure(library)));
            }
            logger.info("Verified " + libraries.size() + " cached Maven dependencies.");
        }
    }
}
