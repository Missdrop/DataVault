# JDBC performance comparison — 2026-09-30

## Method

Local environment: Java 25, HikariCP 7.1.0, SQLite JDBC 3.53.4.0, MySQL
Connector/J 9.4.0, and MariaDB 13.0.2 for the MySQL connection path.
Two independent test JVM runs each took approximately 70 seconds. Both database
tests passed in each run, including verification of the final balance after all updates.

Three paths share the same validated HikariCP pool and run one at a time:

- direct-sync: borrow a connection and execute JDBC on the calling thread.
- direct-async: independently implemented bounded single-worker executor,
  CompletableFuture, and JDBC, representing a plugin's own asynchronous implementation.
- datavault: use the public execute/transaction API.

Both asynchronous paths use one worker and a queue capacity of 256. Pool size
is one. SQL, parameters, transaction boundaries, connection borrowing, and database
settings are identical. SQLite uses WAL without deliberately reducing the
synchronous durability setting. MySQL batch rewriting is not enabled.

Each workload has two warm-up rounds and six measurement rounds. Path order
rotates every round. Each round includes 1,500 indexed reads, 400 individual
updates, or 60 transactions containing 100 updates each.
Throughput is the median of six rounds. p50/p95 use all operation latency samples
from those measurement rounds. Timing includes submission, waiting, connection
borrowing/return, SQL execution, and commit. Batch throughput is transactions
per second, not updated rows per second.

This is a closed-loop workload with one outstanding request, not a concurrent
saturation test or JMH benchmark. Pool and table creation are outside timing.
Only the uniquely named datavault_test_bench_ table is dropped afterward.
SQLite uses a temporary dedicated file; the MariaDB datavault_test database is retained.

## Comparison with independently implemented asynchronous JDBC

Throughput change = DataVault / direct-async - 1. Positive values mean higher
DataVault throughput. Latencies are in microseconds, shown as direct-async /
DataVault from the second JVM run.

| Backend | Workload | Run 1 throughput change | Run 2 throughput change | p50 (µs) | p95 (µs) |
| --- | --- | ---: | ---: | ---: | ---: |
| SQLite | Indexed read | -6.6% | -3.4% | 10.8 / 11.2 | 17.6 / 19.2 |
| SQLite | Single auto-commit update | 0.7% | 0.3% | 2039.9 / 2038.3 | 2212.1 / 2160.3 |
| SQLite | Transaction with 100 updates | -1.3% | -1.5% | 2043.5 / 2039.0 | 2556.1 / 2820.0 |
| MariaDB | Indexed read | 4.2% | 3.8% | 187.3 / 202.9 | 302.2 / 300.0 |
| MariaDB | Single auto-commit update | 1.1% | -4.0% | 2852.3 / 2915.1 | 3167.3 / 3170.4 |
| MariaDB | Transaction with 100 updates | 12.2% | 2.4% | 9084.4 / 9631.5 | 15958.0 / 15080.7 |

## Findings and limitations

Under these settings, common write workloads perform close to independently
implemented asynchronous JDBC. Fast SQLite reads have measurable overhead:
throughput is 3.4–6.6% lower and median latency increases by approximately
0.4–0.8 microseconds. SQLite batch transaction throughput is 1.3–1.5% lower;
its p95 also increases in the second run, so median results alone are insufficient.

Synchronous SQLite reads have a median latency of 3.5 microseconds, compared with
11.2–11.3 microseconds through DataVault. The result does not support a claim of
matching synchronous JDBC in every scenario. Thread handoff and waiting contribute
to this difference. Overhead is much smaller relative to already asynchronous JDBC.

Absolute MariaDB read throughput varies substantially between JVM runs, indicating
machine or database state variability. A lead in a single run is not a proven
performance advantage. These two runs do not establish statistical significance
or production performance guarantees. Concurrent saturation, long transactions,
remote databases, and multiple plugins competing for CPU/disk need separate tests.
No runtime performance implementation was changed during this comparison.

## Reproduction

Set DATAVAULT_TEST_USER and DATAVAULT_TEST_PASSWORD, then run:

```text
./gradlew :plugin:jdbcBenchmark
```

The test connects to 127.0.0.1:3306 and uses the dedicated datavault_test database.
Normal build/test tasks do not automatically run this benchmark.

## Raw observations

Format: RESULT,backend,workload,path,operations_per_second,p50_microseconds,
p95_microseconds,throughput_ratio_to_async_baseline.

First independent JVM run:

```csv
RESULT,SQLite,indexed-read,direct-sync,276029.1,3.5,4.1,3.7150
RESULT,SQLite,indexed-read,direct-async,74300.7,10.5,41.8,1.0000
RESULT,SQLite,indexed-read,datavault,69421.3,11.3,43.1,0.9343
RESULT,SQLite,single-update,direct-sync,491.1,2040.0,2175.5,0.9986
RESULT,SQLite,single-update,direct-async,491.8,2041.6,2308.4,1.0000
RESULT,SQLite,single-update,datavault,495.1,2038.2,2110.0,1.0067
RESULT,SQLite,transaction-100,direct-sync,488.0,2038.4,2666.0,0.9798
RESULT,SQLite,transaction-100,direct-async,498.0,2040.3,2119.7,1.0000
RESULT,SQLite,transaction-100,datavault,491.5,2036.7,2128.1,0.9869
SERVER,13.0.2-MariaDB
RESULT,MariaDB,indexed-read,direct-sync,12708.4,55.6,182.4,1.2578
RESULT,MariaDB,indexed-read,direct-async,10103.3,80.1,253.7,1.0000
RESULT,MariaDB,indexed-read,datavault,10523.9,77.7,266.3,1.0416
RESULT,MariaDB,single-update,direct-sync,378.3,2877.2,3145.3,1.0416
RESULT,MariaDB,single-update,direct-async,363.2,2923.2,3159.2,1.0000
RESULT,MariaDB,single-update,datavault,367.3,2906.7,3151.5,1.0114
RESULT,MariaDB,transaction-100,direct-sync,112.6,8178.1,12777.4,1.1093
RESULT,MariaDB,transaction-100,direct-async,101.5,8968.6,17428.0,1.0000
RESULT,MariaDB,transaction-100,datavault,113.9,8285.8,16937.6,1.1217
```

Second independent JVM run:

```csv
RESULT,SQLite,indexed-read,direct-sync,282432.3,3.5,6.0,3.4542
RESULT,SQLite,indexed-read,direct-async,81764.6,10.8,17.6,1.0000
RESULT,SQLite,indexed-read,datavault,78962.3,11.2,19.2,0.9657
RESULT,SQLite,single-update,direct-sync,494.5,2039.2,2108.4,1.0075
RESULT,SQLite,single-update,direct-async,490.8,2039.9,2212.1,1.0000
RESULT,SQLite,single-update,datavault,492.4,2038.3,2160.3,1.0032
RESULT,SQLite,transaction-100,direct-sync,495.8,2034.1,2109.4,1.0088
RESULT,SQLite,transaction-100,direct-async,491.4,2043.5,2556.1,1.0000
RESULT,SQLite,transaction-100,datavault,484.1,2039.0,2820.0,0.9851
SERVER,13.0.2-MariaDB
RESULT,MariaDB,indexed-read,direct-sync,6777.9,101.4,275.7,1.3390
RESULT,MariaDB,indexed-read,direct-async,5061.8,187.3,302.2,1.0000
RESULT,MariaDB,indexed-read,datavault,5255.5,202.9,300.0,1.0383
RESULT,MariaDB,single-update,direct-sync,383.2,2826.4,3149.7,1.0092
RESULT,MariaDB,single-update,direct-async,379.7,2852.3,3167.3,1.0000
RESULT,MariaDB,single-update,datavault,364.5,2915.1,3170.4,0.9600
RESULT,MariaDB,transaction-100,direct-sync,107.8,8948.8,14215.4,1.1045
RESULT,MariaDB,transaction-100,direct-async,97.6,9084.4,15958.0,1.0000
RESULT,MariaDB,transaction-100,datavault,100.0,9631.5,15080.7,1.0240
```
