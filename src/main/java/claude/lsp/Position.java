package claude.lsp;

public record Position(int line, int character) {

	public String toString1Based() {
		return "%d:%d".formatted(line + 1, character + 1);
	}

}