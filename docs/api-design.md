# API design — first milestone

The API is Java 11 compatible and has no Bukkit, HikariCP or driver dependencies.
This milestone defines contracts and validated configuration, not a working
database provider. Runtime implementation and live database tests follow next.

## Responsibilities

- `DataVault`: owner registration and resource lifecycle.
- `Database`: one owner's asynchronous operations and transactions.
- `SqlOperation`: scoped JDBC work, returning detached results.
- `PluginId`: validated owner identity.
- `config`: immutable backend, pool and admission settings.
- `exception`: explicit overload failure.

The registry must not execute SQL or construct driver-specific settings itself.
Runtime code should separate registration, bounded execution, transaction handling
and SQLite/MySQL connection factories. Use composition to inject these components.
Each owner gets its own pool and execution queue. SQLite also owns a dedicated
local file and a single worker; MySQL has independently sized workers and pool.
Shared JVM, disk and MySQL server resources remain shared.

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

## Design references

- [HikariCP configuration and data source separation](https://github.com/brettwooldridge/HikariCP#initialization):
  separate configuration from resource ownership; do not expose the pool in the public API.
- [Jdbi scoped handles and transactions](https://jdbi.org/releases/3.33.0/apidocs/org/jdbi/v3/core/Handle.html):
  callback-scoped resource use and explicit transaction boundaries.

These are design references; no source code was copied.
