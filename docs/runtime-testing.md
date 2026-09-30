# Runtime verification

## Running tests

`./gradlew test` runs configuration, bounded-worker and real SQLite file tests.
`./gradlew :plugin:mariaDbTest` runs opt-in local MariaDB tests using
DATAVAULT_TEST_USER and DATAVAULT_TEST_PASSWORD environment variables. Credentials
are never checked into source. The test connects to 127.0.0.1:3306, creates the
dedicated datavault_test database if needed, and drops only its uniquely named
datavault_test_ table. The empty database is retained for subsequent runs.

Tests verify commits, rollbacks, connection-state reset, WAL, pool cleanup,
duplicate registration, SQLite file conflicts, global resource budgets, queue
overflow, draining shutdown and independence during a slow MariaDB query.

## Local workload observation — 2026-09-30

One execution on the development machine using MariaDB 13.0.2:

| Backend | Transaction batch | Elapsed | Observed throughput |
| --- | ---: | ---: | ---: |
| SQLite | 5,000 inserts | 13.09 ms | 382,018 rows/s |
| MySQL driver against MariaDB | 2,000 inserts | 184.44 ms | 10,844 rows/s |

These are single-run integration workload observations, including scheduling and
commit, not warmed-up benchmarks or production performance guarantees. Database
durability, batch behavior and protocol differ; do not compare backends using
these numbers. Both runs verify the final row count.

## Current limits

- SQLite reads and writes share one worker. Heavy reads can delay writes.
- MySQL defaults to a 3-second connect and 10-second socket timeout; explicit
  URL options can override them. A socket timeout is not a transaction deadline.
- Arbitrary application callbacks can block forever. Shutdown drains work and
  has no forced deadline yet; the API does not promise bounded shutdown time.
- Completion handlers may run on database workers and must be short. Heavy
  handlers should use thenApplyAsync with an explicitly supplied executor.
- Global queue reservations count task slots, not bytes retained by closures.
- Canonical-path checks handle symlinks but do not enforce isolation against
  hard links or external processes replacing files during registration.
- MySQL compatibility is verified locally against MariaDB, not a MySQL server.
- Java 11 is the compilation target; this test run used Java 25. A Java 11
  runtime compatibility run and live Bukkit server smoke test are still needed.

Further performance work should focus on query/queue deadlines, finite shutdown,
SQLite read concurrency, and warmed-up latency distributions rather than changing
pool size blindly.
