package dev.argon.jawawasm.format.text;

/**
 * A script command with additional context.
 * @param command The command.
 * @param lineNumber The line of the file containing the command.
 * @param expr The expression parsed to get this command.
 */
public record ScriptCommandInfo(ScriptCommand command, int lineNumber, SExpr expr) {
}
