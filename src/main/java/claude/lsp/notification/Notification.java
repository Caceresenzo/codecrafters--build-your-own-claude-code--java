package claude.lsp.notification;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "method", defaultImpl = Notification.Unknown.class)
@JsonSubTypes({
	@JsonSubTypes.Type(value = Notification.PublishDiagnostics.class, name = "textDocument/publishDiagnostics")
})
public sealed interface Notification {

	public record PublishDiagnostics(
		Params params
	) implements Notification {

		public Path path() {
			return Path.of(params.uri());
		}

		public Integer version() {
			return params.version();
		}

		public List<Diagnostic> diagnostics() {
			return params.diagnostics();
		}

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record Params(
			URI uri,
			Integer version,
			List<Diagnostic> diagnostics
		) {}

	}

	public record Unknown(
		JsonNode params
	) implements Notification {}

}