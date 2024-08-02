package jvmwasm.format.types;

import java.util.List;

public record ResultType(List<? extends ValType> types) {
}
