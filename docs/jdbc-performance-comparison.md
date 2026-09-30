# JDBC 性能对照 — 2026-09-30

## 测试方法

本地 Java 25、HikariCP 7.1.0、SQLite JDBC 3.53.4.0、MySQL Connector/J
9.4.0，MySQL 路径实际连接 MariaDB 13.0.2。进行了两个独立测试 JVM 的运行，
每次约 70 秒，均完成两项数据库测试，零失败，并验证所有更新后的最终余额。

三条路径共用同一个已验证的 HikariCP 池，每次只运行一条路径：

- direct-sync：调用线程直接借连接并执行 JDBC。
- direct-async：插件自行实现的单线程有界队列 + CompletableFuture + JDBC。
- datavault：通过公开 execute/transaction 接口运行。

两种异步路径的执行并发均为 1、队列容量 256，池大小为 1。
使用完全相同的 SQL、参数、事务边界、连接借还和数据库设置。
SQLite 使用 WAL，未主动调低同步持久化级别。MySQL 未启用额外的批量重写优化。

每项负载先预热 2 轮，再测量 6 轮；每轮旋转三条路径的先后顺序。
读取每轮 1,500 次，单条更新 400 次，100 条更新的批量事务 60 次。
吞吐取 6 轮的中位数；p50/p95 取这 6 轮全部操作的端到端延迟样本。
时间包含提交、等待、连接借还、SQL 执行与事务提交。
批量事务吞吐单位是事务/秒，不是更新行数/秒。

本次是单个请求完成后才提交下一个的闭环负载，不是并发饱和测试，也不是 JMH。
数据源和表在计时前创建，完成后仅删除本次唯一的 datavault_test_bench_ 表。
SQLite 使用临时独立文件；MariaDB 的 datavault_test 测试库保留。

## 对自行异步 JDBC 的结果

吞吐差异 = DataVault / direct-async - 1；正值表示 DataVault 的吞吐较高。
延迟单位为微秒，列中顺序是 direct-async / DataVault；延迟列展示第二次运行。

| 数据库 | 负载 | 第一次吞吐差异 | 第二次吞吐差异 | p50（µs） | p95（µs） |
| --- | --- | ---: | ---: | ---: | ---: |
| SQLite | 主键读取 | -6.6% | -3.4% | 10.8 / 11.2 | 17.6 / 19.2 |
| SQLite | 单条自动提交更新 | 0.7% | 0.3% | 2039.9 / 2038.3 | 2212.1 / 2160.3 |
| SQLite | 事务内 100 条批量更新 | -1.3% | -1.5% | 2043.5 / 2039.0 | 2556.1 / 2820.0 |
| MariaDB | 主键读取 | 4.2% | 3.8% | 187.3 / 202.9 | 302.2 / 300.0 |
| MariaDB | 单条自动提交更新 | 1.1% | -4.0% | 2852.3 / 2915.1 | 3167.3 / 3170.4 |
| MariaDB | 事务内 100 条批量更新 | 12.2% | 2.4% | 9084.4 / 9631.5 | 15958.0 / 15080.7 |

## 结论与边界

本次配置下，DataVault 与插件自行异步 JDBC 的常见写入负载性能接近。
SQLite 快速读取存在可测的额外开销：吞吐低 3.4%～6.6%，
中位延迟增加约 0.4～0.8 微秒。SQLite 批量事务吞吐低约 1.3%～1.5%；
第二次运行该负载的 p95 也有所增加，不应只看平均或中位数。

同步直连的 SQLite 读取中位延迟为 3.5 微秒，DataVault 为 11.2～11.3 微秒。
因此“所有场景都不比同步 JDBC 差”不成立；主要差异来自异步线程交接与等待。
对照插件已经自行异步执行 JDBC 时，额外开销明显较小。

MariaDB 读取的绝对吞吐在两个 JVM 中差异较大，说明机器或数据库状态有明显波动。
DataVault 的单次领先不能解释为确定的性能优势，也不能用这两次运行建立
统计显著性或生产性能保证。并发饱和吞吐、长事务、远程数据库以及多插件同时
竞争 CPU/磁盘仍需要另外的测试。本次未改变运行时性能实现。

## 复现

设置 DATAVAULT_TEST_USER 和 DATAVAULT_TEST_PASSWORD 环境变量后执行：

```text
./gradlew :plugin:jdbcBenchmark
```

测试固定连接本地 127.0.0.1:3306，使用专用 datavault_test 库。
常规 build/test 不会自动执行这项基准测试。

## 原始结果

格式为 RESULT,数据库,负载,路径,操作每秒,p50微秒,p95微秒,相对异步基线吞吐比值。

第一次独立 JVM：

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

第二次独立 JVM：

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
