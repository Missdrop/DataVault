# Backend configuration and performance contracts

All owners have independent admission and resource lifecycles. JDBC engines use
HikariCP 7.1.0, requiring Java 11+. Configuration and providers are separated by
backend package. No ORM, SQL translation, JSON conversion or global operation lock
is added. Async JDBC scheduling still has a measurable fixed cost.

| Backend | Targeted defaults | Important boundary |
| --- | --- | --- |
| SQLite | WAL, foreign keys, busy timeout, 8 MiB cache; retained connection and writer | Dedicated file; durability is not weakened for benchmarks |
| MySQL | Native data source, server-prepared statements, bounded 256-statement cache | Batch rewriting is URL opt-in because update counts may change |
| MariaDB | Native driver, binary prepared protocol, statement cache, eligible bulk batches | Uses MariaDB-specific protocol optimizations, not the MySQL driver |
| PostgreSQL | prepareThreshold=5, bounded statement cache, TCP keepalive | reWriteBatchedInserts is URL opt-in; server locks remain shared |
| H2 | Retained MVStore connection, 8 MiB cache, no JVM exit hook | File configuration is a base path; actual file ends with .mv.db |
| DuckDB | Retained instance, one writer, two native threads, 256 MiB engine budget | Not a total RSS limit; unwrap native connections for Appender workloads |
| ClickHouse | JDBC/HTTP reuse, driver compression, native insert batches | No ordinary JDBC transactions; async_insert and RowBinary are opt-in |
| MongoDB | Separate MongoClient, bounded per-server pools/workers, direct BSON/cursors | Extra monitoring sockets; use native insertMany/bulkWrite for bulk workloads |
| Redis | Separate multiplexed Lettuce connection/I/O; native async admission | No worker hop; no blocking commands or connection-global transactions/state changes |

ClickHouse uses the ordinary com.clickhouse:jdbc-v2 module with the explicit
com.clickhouse.jdbc.Driver class. No :all classifier or legacy JDBC facade/HTTP
transport is included. Required shared client/data libraries remain transitive
dependencies; the bootstrap downloads them from Maven Central without bundling them.

Network JDBC defaults use three-second connection and ten-second socket deadlines;
PostgreSQL uses seconds and the other drivers milliseconds. Explicit URL settings
remain driver-authoritative. MongoDB URI settings may override network defaults,
but configured pool limits remain authoritative. Redis defaults to a three-second
command deadline and 256 pending operations, rejecting commands during disconnect.

## Registration

```java
var owner = PluginId.of("economy");
vault.register(owner, PostgresqlConfig.of(jdbcUrl, username, password));
vault.register(owner, H2Config.of(dataFolder.resolve("economy")));
vault.register(owner, MongoConfig.of(mongoUri, "economy"))
    .thenCompose(storage -> storage.execute(database ->
        database.getCollection("players").countDocuments()));
vault.register(owner, RedisConfig.of(redisUri))
    .thenCompose(storage -> storage.execute(commands -> commands.get("economy:balance")));
```

These are alternatives: an owner cannot register twice. Use findStorage(owner)
for any backend, find(owner) for JDBC only, or find(owner, RedisStorage.class).
Close drains work before releasing the owner. Embedded file conflicts fail instead
of silently sharing another plugin's database. Files remain after close.
H2 paths containing semicolons and SQLite paths containing question marks are
rejected: JDBC would interpret them as options and undermine literal file ownership.

Consumers using MongoDB/Redis callbacks must add matching compile-only native
drivers. Do not bundle conflicting versions in dependent Bukkit plugins. Declare
`depend: [DataVault]`; the server plugin provides the API and drivers. JDBC-only
consumers need only DataVault's API and java.sql. Hikari and bootstrap helpers are
private and relocated. MongoDB/Lettuce callback types retain their original packages
and live in the same plugin class loader as the API. Avoid bundling competing native
client versions in dependent plugins.

## Installation

`./gradlew build` produces `plugin/build/libs/DataVault-<version>.jar`. Copy only
that JAR into `plugins`; there is no installation ZIP or companion library folder.
Shadow includes only the small, relocated Libby/ASM/jar-relocator bootstrap helpers,
not Hikari or any database driver. The separate Maven API artifact remains a normal
library JAR.

During `onLoad`, the bootstrap downloads the entire build-resolved runtime dependency
closure from Maven Central into `plugins/DataVault/libraries`. The first startup
requires network access and may take longer because embedded native drivers are large.
All versions, sizes and SHA-256 hashes are embedded in a build-generated catalog;
there is no server-side POM resolution, dynamic version selection or `:all` driver.
Subsequent starts verify and reuse cached artifacts without downloading them again.
Downloads are streamed, bounded and published only after validation. Corrupt cache
entries are repaired; failure prevents service registration and disables the plugin.

Verified dependencies are attached to Bukkit's URLClassLoader using Libby's helper;
no Paper-specific API or new Bukkit `libraries` field is required. Hikari is relocated
from the verified original at startup to match the implementation's Shadow-rewritten
references. Public MongoDB/Lettuce packages must not be blindly relocated. Arbitrary
server/other-plugin dependency conflicts are still possible for those public packages;
the tests are not a guarantee against every server's preloaded library versions.

This is startup loading, not per-backend lazy downloading. Hot reload is unsupported;
restart the server after replacing the JAR. The previous ZIP's `DataVault-libraries`
directory is no longer used; it is not deleted automatically.

Design references: [LuckPerms selective Shadow/relocation](https://github.com/LuckPerms/LuckPerms/blob/master/bukkit/build.gradle)
and [Libby runtime classpath loading](https://github.com/AlessioDP/libby#usage).

## Isolation and performance

Per-owner queues and pools prevent one plugin from exhausting another's local
admission or connections. They cannot isolate shared database-server locks, Redis
keys, MongoDB collections, disk, JVM or network. Use dedicated schemas/databases/key
prefixes for data isolation and bounded queries/batches. Larger pools are not
automatically faster.

The fair baseline is a plugin implementing equivalent asynchronous driver access,
not a blocking query on the Minecraft main thread. Native batch APIs remain available.
No abstraction can promise to outperform every hand-tuned workload; measured reports
are in reports/performance.

References: [HikariCP](https://github.com/brettwooldridge/HikariCP),
[pgJDBC](https://jdbc.postgresql.org/documentation/use/),
[MariaDB Connector/J](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j),
[DuckDB](https://duckdb.org/docs/stable/connect/concurrency),
[ClickHouse JDBC](https://clickhouse.com/docs/integrations/java/jdbc-v2),
[MongoDB pools](https://www.mongodb.com/docs/drivers/java/sync/current/connection/specify-connection-options/connection-pools/),
[Lettuce async](https://redis.github.io/lettuce/user-guide/async-api/).
