package dev.argon.jawawasm.format.text;

import dev.argon.jawawasm.format.ModuleFormatException;
import org.jspecify.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a wast script.
 *
 * Only partially parses the format.
 */
public class ScriptReader {
	/**
	 * Creates a script reader.
	 * @param reader The text reader.
	 */
	public ScriptReader(Reader reader) {
		this.reader = reader;
	}

	private final Reader reader;

	private boolean isEOF;
	private final char[] lookahead = new char[2];
	private int lookaheadSize = 0;

	private int tryNextChar() throws IOException {
		if(lookaheadSize > 0) {
			char ch = lookahead[0];
			for(int i = 0; i < lookaheadSize - 1; ++i) {
				lookahead[i] = lookahead[i + 1];
			}
			--lookaheadSize;
			return ch;
		}
		else if(isEOF) {
			return -1;
		}
		else {
			int ch = reader.read();
			if(ch < 0) {
				isEOF = true;
			}
			return ch;
		}
	}

	private char nextChar() throws IOException {
		int ch = tryNextChar();
		if(ch < 0) {
			throw new EOFException();
		}

		return (char)ch;
	}



	private int peek(int n) throws IOException {
		if(lookaheadSize < n) {
			return lookahead[n];
		}

		if(isEOF) {
			return -1;
		}

		while(n >= lookaheadSize) {
			int ch = reader.read();
			if(ch < 0) {
				isEOF = true;
				return -1;
			}

			lookahead[lookaheadSize] = (char)ch;
			++lookaheadSize;
		}

		return lookahead[n];
	}


	/**
	 * Read expressions.
	 * @return The S-Expressions
	 * @throws IOException if an underlying IO error occurs.
	 * @throws ModuleFormatException If the format is invalid.
	 */
	public List<? extends SExpr> readExpressions() throws IOException, ModuleFormatException {
		List<SExpr> exprs = new ArrayList<>();
		while(true) {
			SExpr expr = tryReadExpr();
			if(expr == null) {
				break;
			}
			exprs.add(expr);
		}

		int ch = tryNextChar();
		if(ch >= 0) {
			throw new ModuleFormatException("Unexpected character: " + (char)ch);
		}

		return exprs;
	}


	/**
	 * Read script commands.
	 * @return The commands
	 * @throws IOException if an underlying IO error occurs.
	 * @throws ModuleFormatException If the format is invalid.
	 */
	public List<? extends ScriptCommand> readCommands() throws IOException, ModuleFormatException {
		var exprs = readExpressions();
		List<ScriptCommand> commands = new ArrayList<>(exprs.size());
		for(var expr : exprs) {
			commands.add(buildCommand(expr));
		}
		return commands;
	}

	private SExpr tryReadExpr() throws IOException, ModuleFormatException {
		skipToNextToken();

		int ch = peek(0);
		if(ch < 0) {
			return null;
		}

		if(Character.isDigit(ch) || ch == '-' || ch == '+') {
			return readNumericExpr();
		}
		else if(ch == '"') {
			return readStringExpr();
		}
		else if(ch == '$' || Character.isLetter(ch)) {
			return readIdentifierExpr();
		}
		else if(ch == '(') {
			return readListExpr();
		}
		else {
			return null;
		}
	}


	private void skipToNextToken() throws IOException {
		for(int ch = peek(0); ch >= 0; ch = peek(0)) {
			if(ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
				nextChar();
				continue;
			}
			else if(ch == ';') {
				ch = peek(1);

				if(ch == ';') {
					for(ch = peek(0); ch >= 0 && ch != '\n'; ch = peek(0)) {
						nextChar();
					}
					if(ch >= 0) {
						nextChar();
					}
					continue;
				}
			}
			else if(ch == '(') {
				ch = peek(1);

				if(ch == ';') {
					nextChar(); // Skip (
					nextChar(); // Skip ;
					skipBlockComment();
					continue;
				}
			}

			break;
		}
	}

	private void skipBlockComment() throws IOException {
		for(char prev = nextChar(), ch = nextChar(); !(prev == ';' && ch == ')'); prev = ch, ch = nextChar()) {
			if(prev == '(' && ch == ';') {
				skipBlockComment();
			}
		}
	}

	private SExpr readNumericExpr() throws IOException {
		var sb = new StringBuilder();
		for(int ch = peek(0); ch >= 0 && (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '-' || ch == '+' || ch == ':'); ch = peek(0)) {
			sb.append(nextChar());
		}
		return new SExpr.NumberValue(sb.toString());
	}

	private SExpr readStringExpr() throws IOException, ModuleFormatException {
		var sb = new StringBuilder();
		sb.append(nextChar());

		for(char ch = nextChar(); ch != '"'; ch = nextChar()) {
			sb.append(ch);
			if(ch == '\\') {
				ch = nextChar();
				sb.append(ch);
				switch(ch) {
					case 't', 'n', '"', '\'', '\\' -> {}
					case '{' -> {
						for(ch = nextChar(); ch != '}'; ch = nextChar()) {
							if(Character.digit(ch, 16) < 0) {
								throw new ModuleFormatException("syntax error");
							}
							sb.append(ch);
						}
						sb.append('}');
					}
					default -> {
						if(Character.digit(ch, 16) < 0) {
							throw new ModuleFormatException("syntax error");
						}

						ch = nextChar();
						sb.append(ch);

						if(Character.digit(ch, 16) < 0) {
							throw new ModuleFormatException("syntax error");
						}
					}
				}
			}
		}
		sb.append('"');

		return new SExpr.StringValue(sb.toString());
	}

	private SExpr readIdentifierExpr() throws IOException, ModuleFormatException {
		var sb = new StringBuilder();

		final String validChars = "!#$%&'*+-./:<=>?@\\^_`|~";

		for(int ch = peek(0); ch >= 0 && (Character.isLetterOrDigit(ch) || validChars.indexOf(ch) >= 0); ch = peek(0)) {
			sb.append(nextChar());
		}

		String id = sb.toString();
		if(id.equals("nan") || id.equals("inf") || id.startsWith("nan:")) {
			return new SExpr.NumberValue(id);
		}
		else {
			return new SExpr.Identifier(id);
		}
	}

	private SExpr readListExpr() throws IOException, ModuleFormatException {
		nextChar(); // Skip (

		List<SExpr> exprs = new ArrayList<>();
		while(true) {
			SExpr expr = tryReadExpr();
			if(expr == null) {
				break;
			}
			exprs.add(expr);
		}

		int ch = nextChar();
		if(ch != ')') {
			throw new ModuleFormatException("Unexpected character: " + (char)ch);
		}

		return new SExpr.ExprList(exprs);
	}


	/**
	 * Gets the constructor of an S-Expression
	 * @param expr The expression.
	 * @return The constructor.
	 * @throws ModuleFormatException if the expression does not have a constructor or the constructor is not a string.
	 */
	public static String getSExprConstructor(SExpr expr) throws ModuleFormatException {
		if(!(expr instanceof SExpr.ExprList listExpr)) {
			throw new ModuleFormatException("syntax error");
		}

		SExpr ctor = listExpr.exprs().get(0);
		if(!(ctor instanceof SExpr.Identifier ctorId)) {
			throw new ModuleFormatException("syntax error");
		}

		return ctorId.name();
	}

	private ScriptCommand buildCommand(SExpr expr) throws ModuleFormatException, IOException {
		return switch(getSExprConstructor(expr)) {
			case "module" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();

				@Nullable String name =
						exprs.size() > 1 &&
								exprs.get(1) instanceof SExpr.Identifier n &&
								n.name().startsWith("$")
						? n.name()
						: null;

				yield new ScriptCommand.ScriptModule(name, expr);
			}
			case "register" -> {
				var args = ((SExpr.ExprList)expr).exprs();
				String importName = ((SExpr.StringValue)args.get(1)).asName();
				@Nullable String name = args.size() > 2 ? ((SExpr.Identifier)args.get(2)).name() : null;
				yield new ScriptCommand.Register(importName, name);
			}
			case "invoke", "get" -> buildScriptAction(expr);
			case "assert_return" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				var action = buildScriptAction(exprs.get(1));

				yield new ScriptCommand.Assertion.AssertReturn(action, exprs.stream().skip(2).toList());
			}
			case "assert_trap" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				var trapExpr = exprs.get(1);
				var message = ((SExpr.StringValue)exprs.get(2)).asName();

				if(getSExprConstructor(trapExpr).equals("module")) {
					yield new ScriptCommand.Assertion.AssertTrapInstantiation(trapExpr, message);
				}
				else {
					var action = buildScriptAction(trapExpr);
					yield new ScriptCommand.Assertion.AssertTrap(action, message);
				}
			}
			case "assert_exhaustion" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				var action = buildScriptAction(exprs.get(1));
				var message = ((SExpr.StringValue)exprs.get(2)).asName();

				yield new ScriptCommand.Assertion.AssertExhaustion(action, message);
			}
			case "assert_malformed" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				var module = exprs.get(1);
				var message = ((SExpr.StringValue)exprs.get(2)).asName();

				yield new ScriptCommand.Assertion.AssertMalformed(module, message);
			}
			case "assert_invalid" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				var module = exprs.get(1);
				var message = ((SExpr.StringValue)exprs.get(2)).asName();

				yield new ScriptCommand.Assertion.AssertInvalid(module, message);
			}
			case "assert_unlinkable" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				var module = exprs.get(1);
				var message = ((SExpr.StringValue)exprs.get(2)).asName();

				yield new ScriptCommand.Assertion.AssertUnlinkable(module, message);
			}
			case String command -> throw new ModuleFormatException("Unknown command: " + command);
		};
	}

	private ScriptCommand.Action buildScriptAction(SExpr expr) throws ModuleFormatException, IOException {
		return switch(getSExprConstructor(expr)) {
			case "invoke" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				@Nullable String name;
				String export;
				int offset;

				switch(exprs.get(1)) {
					case SExpr.Identifier(var n) -> {
						name = n;
						export = ((SExpr.StringValue)exprs.get(2)).asName();
						offset = 3;
					}
					case SExpr.StringValue s -> {
						name = null;
						export = s.asName();
						offset = 2;
					}
					default -> throw new ModuleFormatException("expected identifier or string");
				}

				yield new ScriptCommand.Action.Invoke(name, export, exprs.stream().skip(offset).toList());
			}

			case "get" -> {
				var exprs = ((SExpr.ExprList)expr).exprs();
				@Nullable String name;
				String export;

				switch(exprs.get(1)) {
					case SExpr.Identifier(var n) -> {
						name = n;
						export = ((SExpr.StringValue)exprs.get(2)).asName();
					}
					case SExpr.StringValue s -> {
						name = null;
						export = s.asName();
					}
					default -> throw new ModuleFormatException("expected identifier or string");
				}

				yield new ScriptCommand.Action.Get(name, export);
			}

			default -> throw new ModuleFormatException("unexpected action");
		};
	}



}
