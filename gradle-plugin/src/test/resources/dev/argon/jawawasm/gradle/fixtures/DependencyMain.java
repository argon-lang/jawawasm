package example;

import example.generated.Application;
import example.library.Library;
import example.library.LibraryHost;
import dev.argon.jawawasm.runtime.ResultI32;
import dev.argon.jawawasm.runtime.RuntimeContext;
import dev.argon.jawawasm.runtime.RuntimeOptions;

/**
 * The application uses a Wasm module already compiled by the library project.
 */
public final class Main {
	private Main() {}

	public static void main(String[] args) {
		RuntimeContext context = () -> new RuntimeOptions();
		var library = new Library(context);
		var app = new Application(context, new LibraryHost(library));
		int answer = ResultI32.get(app.answer()).item0;
		if(answer != 42) {
			throw new AssertionError("Expected 42, got " + answer);
		}
		System.out.println(answer);
	}

	public static Class<Application> applicationType() {
		return Application.class;
	}

	public static Class<Library> libraryType() {
		return Library.class;
	}
}
