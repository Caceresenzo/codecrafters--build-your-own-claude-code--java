package claude.lsp;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

// Only needed for the LSP extension.
// You can ignore this file until you reach that extension.
// Course-supplied transport; pass JSON using your existing JSON library.
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import lombok.SneakyThrows;

public final class LspClient {

	private final String url;
	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = JsonMapper.builder()
		.enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
		.build();

	public final Map<String, String> extensionToLanguage;

	public static LspClient fromEnvironment() {
		if (System.getenv("CODECRAFTERS_LSP_URL") == null) {
			return null;
		}

		return new LspClient();
	}

	@SneakyThrows
	public LspClient() {
		url = System.getenv("CODECRAFTERS_LSP_URL");
		if (url == null) {
			throw new IllegalStateException("Run with the course LSP transport");
		}

		extensionToLanguage = call("configuration", Map.of(), Configuration.class).extensionToLanguage();
	}

	@SneakyThrows
	public <T> T call(String action, Object body, Class<T> responseType) {
		final var uri = URI.create(url + "/" + action);
		final var bodyString = objectMapper.writeValueAsString(body);

		System.err.println("lsp: %s: >> %s".formatted(uri, bodyString));

		var request = HttpRequest.newBuilder(URI.create(url + "/" + action))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(bodyString)).build();

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException(response.body());
		}

		final var responseBody = response.body();
		System.err.println("lsp: %s: << %s".formatted(uri, responseBody));

		if (responseType == Void.class) {
			return null;
		}

		return objectMapper.readValue(responseBody, responseType);
	}

	public Definition queryDefinition(Path path, int line, int character) {
		final var textDocument = new TextDocument(path.toUri().toString());
		final var position = new Position(line - 1, character - 1);

		return call("textDocument/definition", new QueryDefinitionBody(textDocument, position), Definition.class);
	}

	public void notifyDocumentDidOpen(Path path, int version, String text) {
		final var textDocument = new TextDocument(
			path.toUri().toString(),
			getLanguageFromExtension(path),
			version,
			text
		);

		call("document/didOpen", new NotifyDidOpenBody(textDocument), Void.class);
	}

	private String getExtensionWithDot(Path path) {
		final var fileName = path.getFileName().toString();

		final var lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
			return "";
		}

		return fileName.substring(lastDotIndex);
	}

	private String getLanguageFromExtension(Path path) {
		return extensionToLanguage.getOrDefault(getExtensionWithDot(path), "unknown");
	}

	private record Configuration(Map<String, String> extensionToLanguage) {}

	private record TextDocument(String uri, @JsonInclude(NON_NULL) String languageId, @JsonInclude(NON_NULL) Integer version, @JsonInclude(NON_NULL) String text) {

		TextDocument(String uri) {
			this(uri, null, null, null);
		}

	}

	private record QueryDefinitionBody(TextDocument textDocument, Position position) {}

	private record NotifyDidOpenBody(TextDocument textDocument) {}

}