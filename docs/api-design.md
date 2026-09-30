# API design

The API targets Java 11 and has no Bukkit or HikariCP dependency. JDBC callbacks
use only java.sql. MongoDB and Redis callbacks reference native driver interfaces
through compile-only API dependencies; the server plugin supplies those drivers
at runtime. See [backend configuration](backends.md).

## Responsibilities

- `DataVault`: owner registration and resource lifecycle.
- `Database`: one owner's asynchronous operations and transactions.
- `Storage`: common identity and draining lifecycle, without invented JDBC capabilities.
- `MongoStorage` / `RedisStorage`: native BSON callbacks / asynchronous Redis commands.
- `SqlOperation`: scoped JDBC work, returning detached results.
- `PluginId`: validated owner identity.
- `config`: immutable backend, pool and admission settings.
- `exception`: explicit overload failure.

The registry must not execute SQL or construct driver-specific settings itself.
Runtime code should separate registration, bounded execution, transaction handling
and backend-specific providers. Each JDBC owner gets its own HikariCP pool and
bounded execution queue. Embedded stores reserve dedicated files and retain one
connection and worker. MongoDB owns a separate MongoClient and workers; Redis owns
a separate connection, I/O resources and native admission controller without a
blocking worker hop. JVM, disk, network and database-server resources remain shared.

Runtime responsibilities are separated into DefaultDataVault (registration),
ResourceBudget (global reservations), EmbeddedFiles (file ownership), PoolFactory
(JDBC setup), StorageFactory (typed dispatch), DatabaseExecutor and AsyncAdmission
(bounded admission), JdbcDatabase (composition),
and Transactions (commit and rollback). Bukkit only registers the DataVault
service and initiates asynchronous cleanup on disable.

## Resource and concurrency contract

Opening is asynchronous. Duplicate registration fails without replacing the
existing handle. A failed open releases any partially allocated resources.
Closing stops admission, drains accepted work, and closes the pool. Registration
must not reuse an owner until its previous database has finished closing.
The registry must coordinate direct handle closure with find/owners results.

Callbacks borrow a connection. They close their own statements and result sets,
but do not close or retain the connection. Transaction callbacks do not change
auto-commit, commit or roll back. Callback failure triggers rollback; secondary
rollback failure is suppressed. JDBC DDL may implicitly commit depending on the
backend, so transaction atomicity is subject to the SQL dialect.

Queue overflow completes exceptionally with DatabaseOverloadedException. Never
use CallerRunsPolicy for database tasks. Canceling a returned stage does not
guarantee cancellation of SQL or rollback. Connection acquisition timeout is not
a query timeout; query deadlines need separate driver-aware runtime support.

## Example

```java
vault.register(PluginId.of("economy"), SqliteConfig.of(dataFolder.resolve("economy.db")))
    .thenCompose(database -> database.transaction(connection -> {
        try (var statement = connection.prepareStatement(
                "UPDATE accounts SET balance = balance + ? WHERE player_id = ?")) {
            statement.setLong(1, 100);
            statement.setString(2, playerId.toString());
            return statement.executeUpdate();
        }
    }));
```

Completion handlers run on unspecified threads. Schedule changes to game state
using the server scheduler. Never call join/get on the server thread.

MongoStorage executes synchronous driver calls on its own workers; close cursors
within callbacks. RedisStorage initiates native asynchronous commands on the caller;
its callback must only submit/compose short nonblocking work. The returned stage
must cover every submitted command so shutdown can drain correctly. Do not use
blocking commands, MULTI/EXEC or connection-state changes on the multiplexed connection.
Use separate key prefixes or Redis logical databases for data isolation; independent
connections do not create independent keyspaces. MongoDB likewise needs distinct
logical databases or collection prefixes if data must be isolated.

ClickHouse rejects Database.transaction before executing its callback. MongoDB and
Redis do not implement Database. SQL/document/key-value migration requires plugin-owned
schema and conversion logic; generic migration is not implemented.

## Design references

- [HikariCP configuration and data source separation](https://github.com/brettwooldridge/HikariCP#initialization):
  separate configuration from resource ownership; do not expose the pool in the public API.
- [Jdbi scoped handles and transactions](https://jdbi.org/releases/3.33.0/apidocs/org/jdbi/v3/core/Handle.html):
  callback-scoped resource use and explicit transaction boundaries.

These are design references; no source code was copied.
