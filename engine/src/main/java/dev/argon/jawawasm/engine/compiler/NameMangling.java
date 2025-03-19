package dev.argon.jawawasm.engine.compiler;

public class NameMangling {
	private NameMangling() {

	}

	public static String escapeName(String name) {
		if(name.isEmpty()) {
			return "\\=";
		}

		return name.replace("\\", "\\-")
			.replace("/", "\\|")
			.replace(".", "\\,")
			.replace(";", "\\?")
			.replace("$", "\\,")
			.replace("<", "\\^")
			.replace(">", "\\_")
			.replace("[", "\\{")
			.replace("]", "\\}")
			.replace(":", "\\!");
	}
}
