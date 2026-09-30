# Runtime verification

Run Gradle on Java 17 or newer; production code uses --release 11.
Use -PtestJavaHome="C:/Program Files/Zulu/zulu-11" to run tests on Java 11.
Other operating systems can supply their equivalent installed Java home.

## Test tasks

- ./gradlew test: configuration, budgets, bounded admission, cancellation, rollback,
  state reset and real SQLite, H2 and DuckDB files.
- ./gradlew :plugin:backendIntegrationTest: six disposable Docker servers, CRUD,
  batches, transaction capabilities, owner isolation and cleanup.
- ./gradlew :plugin:backendBenchmark: all nine engines against direct drivers.
  Warm-up and rotated repeated rounds; no speed-dependent assertions. Requires Docker.
- ./gradlew :plugin:packagedBackendTest: real backends using only the shaded
  plugin JAR and JUnit; verifies service descriptors and Hikari relocation.
- ./gradlew :plugin:mariaDbTest: optional local MariaDB at 127.0.0.1:3306.
  Set DATAVAULT_TEST_USER and DATAVAULT_TEST_PASSWORD. Only dedicated datavault_test_
  tables are modified; the datavault_test database is retained.
- ./gradlew :plugin:jdbcBenchmark: original SQLite/local MariaDB comparison,
  requiring the same local credentials.

Pull images before Docker tasks to separate downloads from startup:

```sh
docker pull mysql:8.4
docker pull mariadb:11.4
docker pull postgres:17
docker pull mongo:7
docker pull redis:7.4
docker pull clickhouse/clickhouse-server:25.8
```

Fixtures use unique datavault-test- names, the datavault.test=true label, ephemeral
loopback ports and no persistent volumes. Startup retries have a deadline; test
assertions are never retried. Containers are removed in finally/close paths.
If a JVM is forcibly killed, inspect containers with that exact label and remove
only leftover test containers, never unrelated Docker resources.

## Interpreting results

Reports and raw measurements belong in [reports/performance](../reports/performance),
not this guide. JDBC paths share an identically tuned pool, identical callbacks
and one worker. Native paths use equivalently configured independently owned clients.
Baselines do not use DataVault admission or transaction code.

Compare DataVault primarily with a plugin-style asynchronous baseline. Synchronous
JDBC has different scheduling costs. Fast embedded reads expose overhead; slower
queries can hide it in noise. These are local closed-loop observations, not guarantees.

## Remaining boundaries

Arbitrary callbacks can block forever. Shutdown drains accepted work without a
forced deadline. Completion handlers must be short; use explicitly supplied executors
for expensive transformations. Queue limits count operations, not bytes. Canonical
paths cannot detect hard links or external file replacement. Pools cannot isolate
shared server locks, CPU, disk, JVM or network.

MongoDB pools are per server and add monitoring sockets. Redis and embedded engines
also own native threads. Application-worker reservations are not an OS thread or
total physical-connection cap. A live Bukkit server smoke test is still separate
from these backend and shaded-JAR tests.
