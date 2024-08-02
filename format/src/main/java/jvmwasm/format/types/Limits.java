package jvmwasm.format.types;

import org.jspecify.annotations.Nullable;

public record Limits(int min, @Nullable Integer max) {
}
