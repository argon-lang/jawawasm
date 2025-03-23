package dev.argon.jawawasm.format.modules;

import com.google.protobuf.ByteString;

/**
 * A data section.
 * @param init The initial data.
 * @param mode The mode.
 */
public record Data(ByteString init, DataMode mode) {
}
