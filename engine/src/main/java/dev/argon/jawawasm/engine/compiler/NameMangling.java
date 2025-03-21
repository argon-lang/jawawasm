package dev.argon.jawawasm.engine.compiler;

/**
 * Mangles a name into a valid JVM bytecode name.
 */
public class NameMangling {
	private NameMangling() {

	}

	/**
	 * Escapes the name according to the rules specified in
	 * <a href="https://web.archive.org/web/20160622140347/https://blogs.oracle.com/jrose/entry/symbolic_freedom_in_the_vm">symbolic freedom in the VM</a>.
	 * @param name The name to be escaped.
	 * @return The escaped name.
	 */
	public static String escapeName(String name) {
		if(name.isEmpty()) {
			return "\\=";
		}

		var sb = new StringBuilder(name.length() + 2);
		boolean hadReplacement = false;

		// Ensure that the escaped name will start with a slash.
		if(!isDangerousChar(name.charAt(0))) {
			sb.append("\\=");
		}

		for(int i = 0; i < name.length(); ++i) {
			char c = name.charAt(i);
			switch(c) {
				case '\\' -> {
					if(i < name.length() - 1 && isEscapeChar(name.charAt(i + 1))) {
						hadReplacement = true;
						sb.append("\\-");
					}
					else {
						sb.append(c);
					}
				}
				case '/' -> {
					hadReplacement = true;
					sb.append("\\|");
				}
				case '.' -> {
					hadReplacement = true;
					sb.append("\\,");
				}
				case ';' -> {
					hadReplacement = true;
					sb.append("\\?");
				}
				case '$' -> {
					hadReplacement = true;
					sb.append("\\%");
				}
				case '<' -> {
					hadReplacement = true;
					sb.append("\\^");
				}
				case '>' -> {
					hadReplacement = true;
					sb.append("\\_");
				}
				case '[' -> {
					hadReplacement = true;
					sb.append("\\{");
				}
				case ']' -> {
					hadReplacement = true;
					sb.append("\\}");
				}
				case ':' -> {
					hadReplacement = true;
					sb.append("\\!");
				}
				default -> sb.append(c);
			}
		}

		if(hadReplacement) {
			return sb.toString();
		}
		else {
			return name;
		}
	}

	private static boolean isDangerousChar(char ch) {
		return switch(ch) {
			case '\\', '/', '.', ';', '$', '<', '>', '[', ']', ':' -> true;
			default -> false;
		};
	}

	private static boolean isEscapeChar(char ch) {
		return switch(ch) {
			case '|', ',', '?', '%', '^', '_', '{', '}', '!', '-', '=' -> true;
			default -> false;
		};
	}
}
