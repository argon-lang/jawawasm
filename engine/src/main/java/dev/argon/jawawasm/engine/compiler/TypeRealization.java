package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;

public record TypeRealization(ClassDesc type, boolean isNullable) {}
