package cn.missdrop.datavault.api;

/** Supported backends; SQL dialects are not automatically translated. */
public enum DatabaseType {
    /** Dedicated local SQLite file. */
    SQLITE,
    /** Dedicated MySQL-compatible connection pool. */
    MYSQL
}
