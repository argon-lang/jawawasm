package dev.argon.jawawasm.format.modules;

import com.google.common.collect.ImmutableMap;
import org.jspecify.annotations.Nullable;

/**
 * The name custom section of a WebAssembly module. Indices are unsigned 32-bit
 * values represented by Java integers.
 * 
 * @param moduleName
 *            The module name, or {@code null} when absent.
 * @param functionNames
 *            Names indexed by function index.
 * @param localNames
 *            Local names indexed first by function index, then by local index.
 * @param typeNames
 *            Names indexed by type index.
 * @param fieldNames
 *            Field names indexed first by type index, then by field index.
 * @param tagNames
 *            Names indexed by tag index.
 */
public record Names(
	@Nullable String moduleName,
	ImmutableMap<Integer, String> functionNames,
	ImmutableMap<Integer, ImmutableMap<Integer, String>> localNames,
	ImmutableMap<Integer, String> typeNames,
	ImmutableMap<Integer, ImmutableMap<Integer, String>> fieldNames,
	ImmutableMap<Integer, String> tagNames
) implements CustomSection {}
