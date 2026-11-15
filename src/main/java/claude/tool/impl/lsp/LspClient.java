package claude.tool.impl.lsp;

// Only needed for the LSP extension.
// You can ignore this file until you reach that extension.
// Course-supplied transport; pass JSON using your existing JSON library.
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

public final class LspClient {

	private final String url;
	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

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

		//		extensionToLanguage = objectMapper.readValue(call("configuration", "{}"), Configuration.class).extensionToLanguage();
		extensionToLanguage = null;
	}

	@SneakyThrows
	public String call(String action, String json) {
		var request = HttpRequest.newBuilder(URI.create(url + "/" + action))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json)).build();

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException(response.body());
		}

		return response.body();
	}

	private record Configuration(Map<String, String> extensionToLanguage) {}

}