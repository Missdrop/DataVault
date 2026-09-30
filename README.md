# DataVault

An asynchronous database API and Bukkit service plugin for Minecraft plugins.
Give each participating plugin its own database resources without duplicating
pool setup, bounded task scheduling and cleanup logic.

[中文使用文档](docs/zh-CN.md) · [API design](docs/api-design.md) ·
[Backend tuning](docs/backends.md) · [Testing](docs/runtime-testing.md)

## Features

- SQLite, MySQL, MariaDB, PostgreSQL, H2, DuckDB and ClickHouse through JDBC/HikariCP.
- Native MongoDB callbacks and asynchronous Redis commands through Lettuce.
- Independent per-owner pools, clients and admission limits; dedicated embedded files.
- Asynchronous registration, operations, transactions and draining shutdown.
- A single installable JAR: drivers are downloaded from Maven Central and cached
  with build-pinned versions, sizes and SHA-256 checksums.
- Selective Shadow/relocation for bootstrap helpers and private Hikari types,
  rather than bundling every database driver.

DataVault is an integration API, not a JDBC interceptor: other plugins must use
the API explicitly. Independent pools do not isolate shared database locks,
disk, network or JVM resources. SQL dialects are not automatically translated.

## Requirements and installation

The API and plugin require Java 11 or newer. Your server may require a newer JVM.
The plugin uses Bukkit API and declares Folia support. Live consumer tests passed
on Purpur 26.3 and Lophine 26.3 using Java 25; this is not a claim that every older
server or third-party plugin combination has been tested.

1. Build with a Java 17+ JVM and the Gradle wrapper:

   ```sh
   ./gradlew build
   ```

   On Windows, use `gradlew.bat`.

2. Copy `plugin/build/libs/DataVault-0.1.0-SNAPSHOT.jar` into the server's `plugins` directory.
3. Start the server. The first startup downloads the complete runtime dependency
   set into `plugins/DataVault/libraries`; network access to Maven Central is needed.
   Later startups verify and reuse the cache.

No ZIP extraction or manual driver installation is required. Download/validation
failure prevents DataVault from enabling. Restart after updates; hot reload is unsupported.

## Add the API to your plugin

The current project version is `0.1.0-SNAPSHOT`. Maven publication metadata is
configured, but this README does not imply that the snapshot is available on Maven
Central. For local development, first run:

```sh
./gradlew :api:publishToMavenLocal
```

Then use a compile-only dependency in your plugin's Gradle build:

```kotlin
repositories {
    mavenLocal() // Local development until a release is published.
    mavenCentral()
}

dependencies {
    compileOnly("cn.missdrop:datavault-api:0.1.0-SNAPSHOT")
}
```

Declare the runtime dependency in your plugin's `plugin.yml`:

```yaml
depend: [DataVault]
```

Do not bundle the DataVault API in your plugin. MongoDB/Redis consumers also need
matching compile-only native clients; see the [Chinese guide](docs/zh-CN.md#开发者接入).

## Quick start: SQLite

Inside your `JavaPlugin`, obtain the service and register a stable owner:

```java
import cn.missdrop.datavault.api.DataVault;
import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.SqliteConfig;

DataVault vault = getServer().getServicesManager().load(DataVault.class);
if (vault == null) {
    throw new IllegalStateException("DataVault service is unavailable");
}
PluginId owner = PluginId.of("economy");

vault.register(owner, SqliteConfig.of(getDataFolder().toPath().resolve("economy.db")))
        .thenCompose(database -> database.execute(connection -> {
            try (var statement = connection.createStatement()) {
                return statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS accounts (id VARCHAR(36) PRIMARY KEY, balance BIGINT NOT NULL)");
            }
        }))
        .whenComplete((result, failure) -> {
            if (failure != null) {
                getLogger().log(java.util.logging.Level.SEVERE, "Database initialization failed", failure);
            }
        });
```

Keep `vault` and `owner` as fields when using them across lifecycle methods. During
your plugin's disable, call `vault.unregister(owner)` and observe its completion
without blocking the server thread.

Callbacks close statements/result sets, not the borrowed connection. Never retain
that connection or call `join()`/`get()` on a server/region thread. Continuations
have no guaranteed game-thread affinity; schedule world/entity access appropriately,
especially on Folia.

## Configuration

`plugins/DataVault/config.yml` controls aggregate resource reservations:

```yaml
limits:
  connections: 48
  workers: 32
  queued-tasks: 8192
```

Each consumer supplies its own backend configuration through the API. These global
limits are not OS-thread, total-memory or physical-socket caps.

## Status and verification

Database operations and resource isolation are implemented. Administrative commands
for viewing/changing another plugin's backend and generic cross-database migration
are **not implemented**. Switching storage requires consumer-owned lifecycle, schema
and data-conversion logic.

```sh
./gradlew test
./gradlew :plugin:packagedBackendTest # Requires Docker; cold runs also need Maven Central.
```

See [runtime verification](docs/runtime-testing.md) for integration, benchmark and
live-server fixture tasks. [Measured results](reports/performance/2026-10-01-multi-backend-comparison.md)
include all nine backends and separate live-server checks. Performance is close to
equivalently asynchronous direct-driver baselines in the tested workloads, not a
universal zero-overhead guarantee.

## License

See [LICENSE](LICENSE).
