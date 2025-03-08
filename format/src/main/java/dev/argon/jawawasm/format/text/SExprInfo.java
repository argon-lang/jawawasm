package dev.argon.jawawasm.format.text;

/**
 * An S-Expression with context information.
 * @param expr The expression.
 * @param lineNumber The line number in the file.
 */
public record SExprInfo(SExpr expr, int lineNumber) {
}
