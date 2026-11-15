package claude.lsp;

public record Position(int line, int character) {

	@Override
	public final String toString() {
		return "%d:%d".formatted(line, character);
	}

}