package cn.missdrop.datavault.bootstrap;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.classloader.URLClassLoaderHelper;
import net.byteflux.libby.logging.adapters.JDKLogAdapter;

/** Uses Libby's JVM-compatible URL injection, keeping public native types in the Bukkit loader. */
final class PluginClasspath extends LibraryManager {
    private final URLClassLoaderHelper helper;

    PluginClasspath(URLClassLoader loader, Path dataFolder, Logger logger) {
        super(new JDKLogAdapter(logger), dataFolder, "libraries");
        addMavenCentral();
        helper = new URLClassLoaderHelper(loader, this);
    }

    @Override
    protected void addToClasspath(Path file) {
        helper.addToClasspath(file);
    }
}
