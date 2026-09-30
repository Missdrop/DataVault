# Multi-backend verification and driver comparison — 2026-10-01

## Outcome

All nine engines passed real backend tests on Java 11. Server engines ran in
disposable Docker containers; SQLite, H2 and DuckDB used real local files. The
final shaded plugin passed 11 tests covering all engines without development
driver/API jars on its classpath. No test containers remain; images are retained.

JDBC uses HikariCP 7.1.0. MongoDB uses independently owned native clients and bounded
workers. Redis uses independently owned native connections and I/O resources,
with asynchronous admission rather than an additional blocking worker hop.

ClickHouse now uses ordinary com.clickhouse:jdbc-v2:0.9.3 and the explicit
com.clickhouse.jdbc.Driver, without :all or the legacy JDBC facade/HTTP module.
The final ClickHouse performance observations below replace the earlier bundled
driver run. Other observations came from the full nine-engine Java 11 run before
that dependency-only change; all nine packaged backends were verified again after it.

## Environment and methodology

- Windows 11; Intel Core Ultra 7 270K Plus, 24 logical processors.
- Zulu OpenJDK 11.0.27+6-LTS for functional, packaged and final benchmark tests.
  Gradle 9.2.1 runs on the separately installed Java 25.
- Docker Desktop Linux containers; Docker Engine 29.5.3, ephemeral loopback ports.
- Two warm-up rounds and six measured rounds, rotating execution order.
- One outstanding operation per path; median throughput across measured rounds.
  p50/p95 pool all measured operation latencies, expressed in microseconds.
- JDBC paths share the exact same pool, SQL callbacks and one worker. Baseline
  executor and transaction implementations are independent of DataVault code.
- Native comparisons use equivalent separately owned clients, pool/thread bounds,
  commands and payloads. Redis compares native asynchronous access directly.
- Embedded workload iterations per round: 1,500 reads, 400 updates, 60 transactions
  of 100 updates. Network SQL: 500 reads, 100 updates, 20 transactions of 100 updates.
- ClickHouse: 500 reads and 30 append batches of 100 rows; no transaction emulation.
- MongoDB: 500 reads, 300 updates, 40 insertMany batches of 20 documents.
  Redis: 1,500 GET, 1,000 SET, 200 MSET batches of 100 keys.
- Final affected values, counts and native responses were checked. No forced
  weaker durability, fake rollback or implicit asynchronous writes were used.

SQLite retains its existing durability settings. Different engines have different
default acknowledgment/durability behavior, so these numbers are not a ranking
between engines. Throughput counts callbacks per second, not individual batch rows.

## Relative throughput

DataVault / direct-async; 100% means equal observed throughput. Variations above
or below 100% are not evidence of a guaranteed improvement or regression.

| Engine | Read / GET | Update / SET | Batch |
| --- | ---: | ---: | ---: |
| SQLite | 95.0% | 99.7% | 103.2% |
| MySQL | 97.8% | 98.8% | 106.0% |
| MariaDB | 104.8% | 100.1% | 99.9% |
| PostgreSQL | 98.3% | 101.3% | 97.7% |
| H2 | 98.6% | 102.7% | 100.2% |
| DuckDB | 98.4% | 100.4% | 97.9% |
| ClickHouse | 99.1% | N/A | 101.6% |
| MongoDB | 100.3% | 101.7% | 102.7% |
| Redis | 100.4% | 99.9% | 101.8% |

ClickHouse has no UPDATE/ordinary JDBC transaction case; its batch is append-100.
MongoDB's batch is insert-many-20; Redis uses mset-100. JDBC transaction batches
contain 100 updates.

Most observations are close to the independently implemented asynchronous baseline.
SQLite's extremely short indexed read still measured about 5% lower throughput
(23.0 vs 24.9 microseconds p50), while its update/transaction workloads were close.
This is measurable scheduling/framework cost, not a zero-overhead guarantee.
Synchronous embedded access is faster because it omits thread handoff; blocking
the Minecraft main thread is not a comparable safe execution contract.

These are one-machine closed-loop observations, not JMH microbenchmarks, saturated
load tests or statistical confidence intervals. Repeat on production hardware and
representative queries before establishing performance thresholds.

## Verification coverage

- API tests: 4 passed; plugin unit/embedded tests: 9 passed.
- Development-classpath Docker tests: 6 passed on Java 11.
- Original benchmark revision's shaded-JAR tests: 11 passed on Java 11 (six servers, three embedded engines,
  two resource-registry cases).
- All-backend benchmark: 9 passed; final unbundled ClickHouse rerun: 1 passed.
- Javadoc generated without warnings; final POM marks native API clients optional.
- Transactions commit/rollback and connection-state reset verified where supported.
  ClickHouse rejects transaction callbacks before executing them.
- One owner's occupied JDBC/MongoDB workers do not occupy another owner's workers.
  Redis connection closure does not close another owner's connection/I/O resources.
- Embedded duplicate canonical files are rejected. Native cancellation retains
  in-flight admission until completion; draining cleanup runs once.
- File paths containing H2/SQLite JDBC option delimiters are rejected before
  allocating resources, preserving the identity of the reserved physical file.

Pool isolation does not isolate shared database locks, schemas/keyspaces, CPU,
disk, network or JVM memory. MongoDB adds per-server pools and monitoring sockets;
global application reservations are not a total OS thread/socket limit. Arbitrary
callbacks may block forever; shutdown currently drains without a forced deadline.
A live Bukkit server/classloader compatibility run is still separate from the
packaged-backend smoke tests. Generic cross-model migration is not implemented.

## Reproduction

```powershell
.\gradlew.bat :api:test :plugin:test :plugin:backendIntegrationTest '-PtestJavaHome=C:\Program Files\Zulu\zulu-11'
.\gradlew.bat :plugin:packagedBackendTest '-PtestJavaHome=C:\Program Files\Zulu\zulu-11'
.\gradlew.bat :plugin:backendBenchmark '-PtestJavaHome=C:\Program Files\Zulu\zulu-11'
```

Do not rebuild artifacts from a second Gradle process while benchmarking: replacing
API jars invalidated a long-running exploratory Java 25 run's lazy class loading.
The subsequent isolated Java 11 run passed all nine engines. Pull images first;
container startup retries are bounded and assertions are not retried.

## Docker image digests

| Image | Repository digest |
| --- | --- |
| mysql:8.4 | sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242 |
| mariadb:11.4 | sha256:70cc072b29b4a89ae07abb2d4da2c64678a7f2dfe092751bb51c87d67dc1338b |
| postgres:17 | sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f |
| mongo:7 | sha256:9854f7139445d766a9523571d6f047530c45547460ffcf8259eb2bf4264632ca |
| redis:7.4 | sha256:c6eabf748fc7a61dbb5a705c78bcf3d6377b1127a97d0ce965c11c44ba46896f |
| clickhouse/clickhouse-server:25.8 | sha256:0152dd511befe6a2c2ef53e930726179669b08116da78500b37c51c96ff5ee77 |

## Driver versions

SQLite 3.53.4.0; MySQL Connector/J 9.4.0; MariaDB Connector/J 3.5.10;
pgJDBC 42.7.13; H2 2.5.252; DuckDB JDBC 1.5.6.0; ClickHouse JDBC v2 0.9.3;
MongoDB sync 5.6.1; Lettuce 6.8.1.RELEASE.

Raw measurements: [CSV](2026-10-01-multi-backend-comparison.csv).
Backend settings and usage: [guide](../../docs/backends.md).

## Thin distribution follow-up

The original benchmark verification above preceded the packaging correction.
Shadow and fat-JAR assembly have now been removed. The plugin contains only its
own implementation and API (67,623 bytes); external dependencies remain unchanged
JARs in the adjacent `DataVault-libraries` directory. The installation ZIP is
124,395,259 bytes: separating libraries does not eliminate their native payloads.
No runtime downloader or Paper-specific loader was added.

`build :plugin:packagedBackendTest` passed on Java 11 after this change: 14 cases,
covering six Docker servers, three embedded engines, two registry cases and three
packaging checks. Packaging checks reject bundled third-party classes, verify
every manifest dependency exists in the ZIP, and initialize all nine driver/client
entry points through a URLClassLoader with only the Java platform as its parent.
The explicit backend-test classpath contains no development driver/API JARs;
external drivers load through the plugin manifest. Test containers were removed.

Performance measurements were not rerun for this packaging-only correction;
the earlier CSV remains historical data, not a new thin-distribution benchmark.
A live Bukkit server smoke test remains outstanding.
