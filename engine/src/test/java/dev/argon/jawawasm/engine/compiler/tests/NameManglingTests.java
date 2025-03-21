package dev.argon.jawawasm.engine.compiler.tests;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.argon.jawawasm.engine.compiler.NameMangling.escapeName;

public class NameManglingTests {

	@Test
	public void testEmptyString() {
		// Empty string should get null prefix \=
		assertEquals("\\=", escapeName(""), "Empty string should be escaped with null prefix");
	}

	@Test
	public void testNoDangerousCharacters() {
		// String with no dangerous characters or escapes should remain unchanged
		assertEquals("HelloWorld", escapeName("HelloWorld"), "String without dangerous characters should not change");
	}

	@Test
	public void testSingleDangerousCharacter() {
		// Each dangerous character should be replaced with its escape sequence
		assertEquals("\\|", escapeName("/"), "/ should be escaped as \\|");
		assertEquals("\\,", escapeName("."), ". should be escaped as \\,");
		assertEquals("\\?", escapeName(";"), "; should be escaped as \\?");
		assertEquals("\\%", escapeName("$"), "$ should be escaped as \\%");
		assertEquals("\\^", escapeName("<"), "< should be escaped as \\^");
		assertEquals("\\_", escapeName(">"), "> should be escaped as \\_");
		assertEquals("\\{", escapeName("["), "[ should be escaped as \\{");
		assertEquals("\\}", escapeName("]"), "] should be escaped as \\}");
		assertEquals("\\!", escapeName(":"), ": should be escaped as \\!");
	}

	@Test
	public void testEscapeCharacter() {
		// Backslash itself should not be escaped
		assertEquals("\\", escapeName("\\"), "Backslash should be escaped as \\");
	}

	@Test
	public void testAccidentalEscape() {
		// Accidental escape sequences should have their backslash escaped
		assertEquals("\\-|", escapeName("\\|"), "Accidental escape \\| should become \\-|");
		assertEquals("\\-,", escapeName("\\,"), "Accidental escape \\, should become \\-,");
		assertEquals("\\-=", escapeName("\\="), "Accidental escape \\= should become \\-=");
	}

	@Test
	public void testMultipleDangerousCharacters() {
		// Multiple dangerous characters should each be escaped
		assertEquals("\\|\\,\\?", escapeName("/.;"), "Multiple dangerous characters should be escaped individually");
	}

	@Test
	public void testMixedContent() {
		// Mix of safe and dangerous characters
		assertEquals("\\=com\\|example\\|MyClass", escapeName("com/example/MyClass"),
			"Package name with slashes should be escaped properly");
	}

	@Test
	public void testNullPrefixWhenChangesMade() {
		// If changes are made and string doesn’t start with \, prepend \=
		assertEquals("\\=abc\\|def", escapeName("abc/def"),
			"String with changes but no leading \\ should get null prefix");
	}

	@Test
	public void testComplexString() {
		// Complex case with dangerous characters and accidental escapes
		assertEquals("\\=Test\\-|\\,\\?\\%\\^\\_\\{\\!",
			escapeName("Test\\|.;$<>[:"),
			"Complex string with accidental escapes and dangerous characters should be fully escaped");
	}

	@Test
	public void testNoChangeNoPrefix() {
		// String with no escapes or dangerous characters should not get prefix
		assertEquals("PlainText", escapeName("PlainText"),
			"String with no changes should not get null prefix");
	}

	@Test
	public void safeBackslash() {
		// Two consecutive backslashes should each be escaped
		assertEquals("x\\a", escapeName("x\\a"), "\\a should remain \\a");
	}

	@Test
	public void endingBackslash() {
		// Two consecutive backslashes should each be escaped
		assertEquals("x\\", escapeName("x\\"), "Backslash and end should not be escaped should become \\-\\-");
	}

	@Test
	public void testDoubleBackslash() {
		// Two consecutive backslashes should not be escaped
		assertEquals("\\\\", escapeName("\\\\"), "Double backslash should remain \\\\");
	}
}
