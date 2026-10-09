package dev.argon.jawawasm.format.types;

/**
 * A value type.
 */
public sealed interface ValType extends StorageType permits NumType, VecType, RefType, BotType {

}
