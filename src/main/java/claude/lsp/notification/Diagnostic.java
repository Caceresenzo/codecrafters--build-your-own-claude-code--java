package claude.lsp.notification;

import claude.lsp.Range;

public record Diagnostic(
	Range range,
	int severity,
	String message,
	String source,
	String code
) {}