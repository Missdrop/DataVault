package cn.missdrop.datavault.runtime.backend.h2;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.h2.H2Config;
import cn.missdrop.datavault.runtime.connection.JdbcBackend;
import com.zaxxer.hikari.HikariConfig;

/** Dedicated MVStore file with a retained connection and bounded page cache. */
public final class H2Backend implements JdbcBackend {
    @Override
    public void configure(DatabaseConfig config, HikariConfig pool) {
        pool.setDriverClassName("org.h2.Driver");
        pool.setJdbcUrl("jdbc:h2:file:" + ((H2Config) config).file()
                + ";DB_CLOSE_ON_EXIT=FALSE;CACHE_SIZE=8192");
        pool.setUsername("sa");
        pool.setPassword("");
    }
}
