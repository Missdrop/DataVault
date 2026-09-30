package cn.missdrop.datavault.runtime.backend.mysql;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.MysqlConfig;
import cn.missdrop.datavault.runtime.connection.JdbcBackend;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.zaxxer.hikari.HikariConfig;

/** Caches repeat prepared SQL in the driver; no application-level statement cache. */
public final class MysqlBackend implements JdbcBackend {
    @Override
    public void configure(DatabaseConfig config, HikariConfig pool) throws Exception {
        MysqlConfig mysql = (MysqlConfig) config;
        MysqlDataSource source = new MysqlDataSource();
        source.setUrl(mysql.jdbcUrl());
        source.setConnectTimeout(3000);
        source.setSocketTimeout(10000);
        source.setCachePrepStmts(true);
        source.setPrepStmtCacheSize(256);
        source.setPrepStmtCacheSqlLimit(2048);
        source.setUseServerPrepStmts(true);
        // URL options remain authoritative. Batch rewriting is opt-in to preserve row-count semantics.
        pool.setDataSource(source);
        pool.setUsername(mysql.username());
        pool.setPassword(mysql.password());
    }
}
