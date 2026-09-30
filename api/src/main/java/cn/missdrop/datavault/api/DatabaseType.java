package cn.missdrop.datavault.api;

/** Supported backends; SQL dialects are not automatically translated. */
public enum DatabaseType {
    /** Dedicated local SQLite file. */
    SQLITE,
    /** Dedicated MySQL-compatible connection pool. */
    MYSQL,
    /** MariaDB using its native JDBC driver. */
    MARIADB,
    /** PostgreSQL with server-prepared statements. */
    POSTGRESQL,
    /** Embedded H2 file. */
    H2,
    /** Embedded DuckDB analytical file. */
    DUCKDB,
    /** ClickHouse JDBC, without simulated transactions. */
    CLICKHOUSE,
    /** MongoDB native client. */
    MONGODB,
    /** Redis native asynchronous client. */
    REDIS
}
