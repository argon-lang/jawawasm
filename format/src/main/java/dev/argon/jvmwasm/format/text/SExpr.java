package dev.argon.jvmwasm.format.text;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * An SExpression used in the WebAssembly text format.
 */
public sealed interface SExpr {
	/**
	 * A number.
	 * @param rawNum The unparsed number value.
	 */
	public static record NumberValue(String rawNum) implements SExpr {
		/**
		 * Convert to an int.
		 * @return The int value.
		 */
		public int intValue() {
			String s = rawNum.replace("_", "");
			if(s.startsWith("0x") || s.startsWith("0X")) {
				return Integer.parseUnsignedInt(s.substring(2), 16);
			}
			else if(s.startsWith("-0x") || s.startsWith("-0X")) {
				return -Integer.parseUnsignedInt(s.substring(3), 16);
			}
			else if(s.startsWith("-")) {
				return Integer.parseInt(s);
			}
			else {
				return Integer.parseUnsignedInt(s);
			}
		}

		/**
		 * Convert to a long.
		 * @return The long value.
		 */
		public long longValue() {
			String s = rawNum.replace("_", "");
			if(s.startsWith("0x")) {
				return Long.parseUnsignedLong(s.substring(2), 16);
			}
			else if(s.startsWith("-0x")) {
				return -Long.parseUnsignedLong(s.substring(3), 16);
			}
			else if(s.startsWith("-")) {
				return Long.parseLong(s);
			}
			else {
				return Long.parseUnsignedLong(s);
			}
		}

		/**
		 * Convert to a float.
		 * @return The float value.
		 */
		public float floatValue() {
			String s = rawNum;
			if(s.startsWith("0x") && !(s.contains("p") || s.contains("P"))) {
				s = s + "p0";
			}

			if(s.equals("nan") || s.equals("+nan")) {
				return Float.NaN;
			}
			else if(s.equals("-nan")) {
				return Float.intBitsToFloat(0xffc00000);
			}
			else if(s.equals("inf") || s.equals("+inf")) {
				return Float.POSITIVE_INFINITY;
			}
			else if(s.equals("-inf")) {
				return Float.NEGATIVE_INFINITY;
			}
			else if(s.startsWith("nan:0x")) {
				int bits = Integer.parseInt(s.replace("_", "").substring(6), 16);
				return Float.intBitsToFloat(0x7F800000 | bits);
			}
			else if(s.startsWith("-nan:0x")) {
				int bits = Integer.parseInt(s.replace("_", "").substring(7), 16);
				return Float.intBitsToFloat(0xFF800000 | bits);
			}
			else {
				return Float.parseFloat(s);
			}
		}

		/**
		 * Convert to a double.
		 * @return The double value.
		 */
		public double doubleValue() {
			String s = rawNum;
			if(s.startsWith("0x") && !(s.contains("p") || s.contains("P"))) {
				s = s + "p0";
			}

			if(s.equals("nan") || s.equals("+nan")) {
				return Double.NaN;
			}
			else if(s.equals("-nan")) {
				return Double.longBitsToDouble(0xfff8000000000000L);
			}
			else if(s.equals("inf") || s.equals("+inf")) {
				return Double.POSITIVE_INFINITY;
			}
			else if(s.equals("-inf")) {
				return Double.NEGATIVE_INFINITY;
			}
			else if(s.startsWith("nan:0x")) {
				long bits = Long.parseLong(s.replace("_", "").substring(6), 16);
				return Double.longBitsToDouble(0x7FF0000000000000L | bits);
			}
			else if(s.startsWith("-nan:0x")) {
				long bits = Long.parseLong(s.replace("_", "").substring(7), 16);
				return Double.longBitsToDouble(0xFFF0000000000000L | bits);
			}
			else {
				return Double.parseDouble(s);
			}
		}

		@Override
		public String toString() {
			return rawNum;
		}
	}

	/**
	 * A string value
	 * @param rawString The unparsed string.
	 */
	public static record StringValue(String rawString) implements SExpr {
		/**
		 * Write the decoted string to a stream.
		 * @param os The output stream
		 * @throws IOException if an error occurs when writing to the stream.
		 */
		public void writeTo(OutputStream os) throws IOException {
			for(int i = 1; i < rawString.length() - 1; ) {
				int ch = rawString.codePointAt(i);
				if(ch == '\\') {
					i += Character.charCount(ch);
					ch = rawString.codePointAt(i);
					switch(ch) {
						case 't' -> os.write((byte)'\t');
						case 'n' -> os.write((byte)'\n');
						case '"' -> os.write((byte)'"');
						case '\'' -> os.write((byte)'\'');
						case '\\' -> os.write((byte)'\\');
						case '{' -> {
							++i;
							int hexStart = i;
							while(rawString.charAt(i) != '}') {
								++i;
							}
							int codepoint = Integer.parseInt(rawString.substring(hexStart, i), 16);
							os.write(new String(Character.toChars(codepoint)).getBytes(StandardCharsets.UTF_8));
						}
						default -> {
							if(Character.digit(ch, 16) < 0) {
								throw new IllegalArgumentException();
							}
							ch = rawString.charAt(i + 1);
							if(Character.digit(ch, 16) < 0) {
								throw new IllegalArgumentException();
							}

							os.write((byte)Integer.parseInt(rawString.substring(i, i + 2), 16));
							++i;
						}
					}
				}
				else {
					os.write(new String(Character.toChars(ch)).getBytes(StandardCharsets.UTF_8));
				}

				i += Character.charCount(ch);
			}
		}

		/**
		 * Gets the decoded value.
		 * @return The decoded value.
		 */
		public String asName() {
			try {
				var os = new ByteArrayOutputStream();
				writeTo(os);
				return os.toString(StandardCharsets.UTF_8);
			}
			catch(IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public String toString() {
			return rawString;
		}
	}

	/**
	 * An identifier.
	 * @param name The name of the identifier.
	 */
	public static record Identifier(String name) implements SExpr {
		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * A list of expression.
	 * @param exprs The expressions.
	 */
	public static record ExprList(List<? extends SExpr> exprs) implements SExpr {
		@Override
		public String toString() {
			return "(" + String.join(" ", exprs.stream().map(Object::toString).toList()) + ")";
		}
	}
}
