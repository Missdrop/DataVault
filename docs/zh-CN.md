# DataVault 中文使用文档

[返回 README](../README.md)

DataVault 为 Minecraft 插件提供异步数据库 API，并通过 Bukkit 服务注册机制共享。
它统一处理连接初始化、资源限额、任务调度和关闭流程，但不会接管其他插件已经写好的 JDBC 代码。
只有主动接入 DataVault API 的插件才能使用这些能力。

## 支持范围

当前支持 SQLite、MySQL、MariaDB、PostgreSQL、H2、DuckDB、ClickHouse、MongoDB 和 Redis。
前七种使用 JDBC 与 HikariCP；MongoDB 使用原生同步驱动，Redis 使用 Lettuce 原生异步命令。
MongoDB 和 Redis 不被伪装成 JDBC 数据库，也不实现 `Database` 接口。

API 与插件运行时最低要求 Java 11，构建工具需要 Java 17 或更新版本。
服务端自身可能要求更高版本，例如本项目提供的 Purpur/Lophine 26.3 实机测试使用 Java 25。
插件基于 Bukkit API，已经声明 Folia 支持，并验证了这两个核心上的实际 API 调用。
这不代表所有旧核心、所有第三方插件组合或高负载区域线程场景都已验证。

## 安装与更新

在项目根目录运行：

```sh
./gradlew build
```

Windows 使用 `gradlew.bat build`。将生成的
`plugin/build/libs/DataVault-0.1.0-SNAPSHOT.jar` 放入服务端的 `plugins` 目录即可。

首次启动时，插件在 `onLoad` 阶段从 Maven Central 下载完整的锁定依赖集合，缓存到
`plugins/DataVault/libraries`。当前不是“使用某一种数据库时才下载对应驱动”，所以首次启动可能较慢，
尤其是包含原生库的嵌入式驱动。后续启动校验并复用缓存，不会每次重新下载。

版本、文件大小与 SHA-256 校验值由构建时生成的依赖清单固定。下载流式写入临时文件，
校验成功后才发布到缓存；损坏的缓存会尝试重新下载，下载或校验失败则不会启用插件。
首次启动需要访问 Maven Central，并具有缓存目录写入权限。

发行物只有一个安装 JAR，不需要 ZIP 或手动携带数据库驱动。Shadow 只打包并重定位少量加载辅助库，
Hikari 在下载后重定位；MongoDB/Lettuce 的公共类型保留原包名，以保持其他插件调用 API 时的类型一致性。
不要在接入插件中再次打包这些公共客户端的冲突版本。

更新时停止服务端、替换 JAR，再重新启动。不支持热重载。
旧 ZIP 方案留下的 `DataVault-libraries` 目录已不再使用；插件不会自动删除它。

## 全局配置

`plugins/DataVault/config.yml` 默认内容为：

```yaml
limits:
  connections: 48
  workers: 32
  queued-tasks: 8192
```

这些是注册资源的全局预留上限，包括仍在打开或排空关闭的资源。它们不是每个插件的配置，
也不是整个 JVM 的内存、原生线程或物理套接字总量上限。
MongoDB 会额外建立监控连接；Redis、DuckDB 等也可能使用驱动自己的线程。

每个接入插件通过 API 指定数据库类型、连接信息、独立连接池和任务限额。
当前服务器配置文件不提供为其他插件直接切换数据库的功能。

## 开发者接入

当前坐标为 `cn.missdrop:datavault-api:0.1.0-SNAPSHOT`。
项目已经配置 Maven 发布元数据，但并不意味着当前快照已经发布到 Maven Central。
本地开发先在 DataVault 项目运行：

```sh
./gradlew :api:publishToMavenLocal
```

接入插件的 Gradle Kotlin DSL：

```kotlin
repositories {
    mavenLocal() // 当前快照用于本地开发；正式发布后使用发布仓库。
    mavenCentral()
}

dependencies {
    compileOnly("cn.missdrop:datavault-api:0.1.0-SNAPSHOT")
}
```

使用 Maven 时，将 API 依赖设为 `provided`，不要打包进自己的插件：

```xml
<dependency>
    <groupId>cn.missdrop</groupId>
    <artifactId>datavault-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

在接入插件的 `plugin.yml` 声明：

```yaml
depend: [DataVault]
```

使用 MongoDB 或 Redis 回调时，还需对应原生客户端的编译期依赖：

```kotlin
dependencies {
    // 仅添加实际使用的客户端；运行时由 DataVault 提供。
    compileOnly("org.mongodb:mongodb-driver-sync:5.6.1")
    compileOnly("io.lettuce:lettuce-core:6.8.1.RELEASE")
}
```

Maven 对这些客户端同样使用 `provided`。仅使用 JDBC 时，不需要原生客户端或 Hikari 的编译期类型。

### 获取服务与注册 SQLite

下面的片段放在 `JavaPlugin` 的启用逻辑中；跨生命周期使用时，把 `vault` 和 `owner` 保存为字段。

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

注册是异步的，返回的阶段完成后才可以使用句柄。一个 owner 同时只能注册一个存储。
重复注册不会覆盖原来的连接；失败会释放预留资源。插件 ID 应稳定且唯一，文件路径应位于自己的数据目录。

### MySQL 与自定义限额

MySQL 可以使用默认配置：

```java
import cn.missdrop.datavault.api.config.MysqlConfig;

var config = MysqlConfig.of("jdbc:mysql://127.0.0.1:3306/economy", username, password);
vault.register(owner, config);
```

这和上面的 SQLite 示例是替代方案，不能为同一个已注册 owner 再次注册。
用户名、密码从自己的安全配置读取；不要硬编码生产凭据或打印含凭据的 URL。
TLS 等连接选项按实际部署配置。

需要调整连接池与排队限额时：

```java
import cn.missdrop.datavault.api.config.ExecutionOptions;
import cn.missdrop.datavault.api.config.PoolOptions;
import java.time.Duration;

var config = new MysqlConfig(jdbcUrl, username, password,
        new PoolOptions(4, 0, Duration.ofSeconds(3)),
        new ExecutionOptions(2, 256));
```

默认网络 JDBC 配置为最多 3 个连接、0 个最低空闲连接、2 个工作线程及 256 个等待任务。
工作线程数不能超过池大小。连接获取超时不是 SQL 执行超时，增大连接池也不一定提高吞吐量。

### 查询与事务

只在初始化成功后调用 `find`，不要在注册尚未完成时立即查找句柄：

```java
var database = vault.find(owner).orElseThrow();
database.execute(connection -> {
    try (var statement = connection.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
        statement.setString(1, playerId.toString());
        try (var rows = statement.executeQuery()) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }
});
```

使用 `database.transaction(...)` 执行事务回调。正常返回时提交，回调失败时尝试回滚。
回调中不要自行 `commit`、`rollback` 或修改 `autoCommit`；DDL 是否隐式提交取决于具体数据库。
ClickHouse 不支持普通 JDBC 事务，调用 `transaction` 会在执行回调前失败。

连接由 DataVault 借出和归还。回调应关闭自己的 statement/result set，但不能关闭或保留连接，
也不能把仍依赖连接的结果对象交给回调外使用。

### MongoDB 与 Redis

```java
import cn.missdrop.datavault.api.config.mongodb.MongoConfig;
import cn.missdrop.datavault.api.config.redis.RedisConfig;

vault.register(PluginId.of("mongo-example"), MongoConfig.of(mongoUri, "economy"))
        .thenCompose(storage -> storage.execute(database ->
                database.getCollection("players").countDocuments()));

vault.register(PluginId.of("redis-example"), RedisConfig.of(redisUri))
        .thenCompose(storage -> storage.execute(commands -> commands.get("economy:balance")));
```

MongoDB 的同步操作在独立工作线程中执行，游标应在回调内关闭。
Redis 回调在调用方线程发起原生异步命令，只能做短小、非阻塞的提交与组合操作；
返回的阶段必须覆盖所有已提交命令，以便正确计数和排空。
不要在共享的单 owner 连接上使用阻塞命令、`MULTI/EXEC`、关闭自动刷新或修改连接级状态。

独立连接不等于独立数据空间：MongoDB 应使用独立逻辑库/集合前缀，Redis 应使用独立键前缀或适用的逻辑库。

### 查找与关闭

- `find(owner)`：查找 JDBC `Database`，MongoDB/Redis 不会返回该句柄。
- `findStorage(owner)`：查找任意已经就绪的存储。
- `find(owner, RedisStorage.class)` 等：按具体能力查找句柄，需要导入对应 API 类型。
- `owners()`：获取当前就绪 owner 的不可变快照。
- `unregister(owner)`：停止接收新任务，排空已接收操作并关闭该 owner 的资源。

插件禁用时调用 `vault.unregister(owner)`，记录关闭阶段的异常，但不要在服务端线程等待它完成。
不要调用 `vault.shutdown()` 来关闭所有插件的资源；全局关闭由 DataVault 插件负责。
重新使用同一个 owner 前，需要等待原来的关闭阶段完成。

## 线程与隔离边界

每个 JDBC owner 有独立 Hikari 池和有界任务队列；SQLite/H2/DuckDB 保留单连接与单工作线程，
并拒绝同时注册同一规范化物理文件。关闭不会删除数据库文件。
独立文件由插件指定路径实现，不是自动为所有未接入插件生成文件。

一个 owner 的队列或连接池耗尽不会直接占用另一个 owner 的本地限额，但共享服务器锁、JVM、磁盘和网络
仍可能影响其他插件。无法保证某个插件永久阻塞或共享数据库服务器过载时，其他插件完全不受影响。

回调和 future continuation 没有游戏线程归属保证。不要在主线程或区域线程使用 `join()`/`get()`，
不要在数据库回调直接操作世界或实体；需要通过相应平台调度器切回正确线程。
Folia 支持声明不会让接入插件的任意回调自动变成线程安全。

队列溢出会异常完成；取消阶段不保证 SQL 已取消或已回滚。
当前关闭会等待已接收任务排空，没有针对永久阻塞回调的强制截止时间。

## 当前未实现的功能

当前没有用于管理其他插件数据库的指令或面板，也没有通用数据库迁移功能。
不会自动把现有第三方插件改为使用 DataVault，不会翻译不同数据库的 SQL。
切换数据库需要插件先完成旧存储关闭，再注册新配置；表结构创建、数据复制和类型转换由接入插件负责。

## 测试与进一步阅读

```sh
./gradlew test
./gradlew :plugin:packagedBackendTest
./gradlew :plugin:serverSmokeJar
```

打包数据库测试需要 Docker，冷启动还需要访问 Maven Central。
`serverSmokeJar` 生成独立测试插件，只能安装到隔离测试服，不能用于生产服。
Java 11 运行时验证可指定 `-PtestJavaHome="你的 JDK 11 目录"`；Gradle 本身仍使用较新的 JVM。

详细说明见 [API 设计](api-design.md)、[后端优化](backends.md)、[测试流程](runtime-testing.md)，
以及 [英文测试与性能记录](../reports/performance/2026-10-01-multi-backend-comparison.md)。
实测结果不构成所有负载下都优于手写 JDBC 的保证。

许可证见 [LICENSE](../LICENSE)。
