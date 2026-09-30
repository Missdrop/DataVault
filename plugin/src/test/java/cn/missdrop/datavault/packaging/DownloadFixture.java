package cn.missdrop.datavault.packaging;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Logger;

/** Loads a single release jar in a plugin-like loader, bootstraps it, then runs real backend tests. */
public final class DownloadFixture {
    private DownloadFixture() { }

    public static void main(String[] arguments) throws Exception {
        URL[] urls = new URL[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            urls[index] = Path.of(arguments[index]).toUri().toURL();
        }
        System.setProperty("datavault.packaged.jar", arguments[0]);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            var bootstrap = Class.forName("cn.missdrop.datavault.bootstrap.DependencyBootstrap", true, loader);
            bootstrap.getMethod("install", Path.class, Logger.class, ClassLoader.class)
                    .invoke(null, Path.of(arguments[0]), Logger.getLogger("DataVault bootstrap test"), loader);
            runTests(loader);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    /** JUnit lives in the child loader; tests and callbacks cannot inherit development APIs. */
    private static void runTests(ClassLoader loader) throws Exception {
        String[] names = {"cn.missdrop.datavault.integration.JdbcDockerTest",
                "cn.missdrop.datavault.integration.NativeDockerTest",
                "cn.missdrop.datavault.runtime.EmbeddedBackendTest",
                "cn.missdrop.datavault.runtime.RegistryTest", "cn.missdrop.datavault.packaging.PackagingTest"};
        Class<?>[] tests = new Class<?>[names.length];
        for (int index = 0; index < names.length; index++) {
            tests[index] = Class.forName(names[index], true, loader);
        }
        Class<?> junit = Class.forName("org.junit.runner.JUnitCore", true, loader);
        Object result = junit.getMethod("runClasses", Class[].class).invoke(null, (Object) tests);
        int failures = (Integer) result.getClass().getMethod("getFailureCount").invoke(result);
        System.out.println("Packaged backend cases: " + result.getClass().getMethod("getRunCount").invoke(result)
                + "; failures: " + failures);
        if (failures != 0) {
            throw new AssertionError(result.getClass().getMethod("getFailures").invoke(result));
        }
        System.out.println("Verified test suites: " + Arrays.toString(names));
    }
}
