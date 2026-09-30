package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;

/** Immutable backend configuration. Never log credentials. */
public interface DatabaseConfig {
    DatabaseType type();
    ExecutionOptions execution();
}
