package cn.missdrop.datavault.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable lowercase plugin identifier, independent of Bukkit. */
public final class PluginId {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private final String value;

    private PluginId(String value) { this.value = value; }

    public static PluginId of(String value) {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid plugin identifier");
        }
        return new PluginId(value);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object other) {
        return other instanceof PluginId && value.equals(((PluginId) other).value);
    }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}
