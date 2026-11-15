package claude.lsp;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

// Only needed for the LSP extension.
// You can ignore this file until you reach that extension.
// Course-supplied transport; pass JSON using your existing JSON library.
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import claude.lsp.notification.Notification;
import claude.lsp.notification.NotificationBatch;
import lombok.SneakyThrows;

public final class LspClient {

	private final String url;
	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = JsonMapper.builder()
		.enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
		.build();

	public final Map<String, String> extensionToLanguage;

	private final Map<Path, Integer> fileVersions = new HashMap<>();
	private final BiFunction<Path, Integer, Integer> increment = (_, oldValue) -> oldValue != null ? oldValue + 1 : 1;

	private int notificationCursor = 0;
	private List<Notification> pendingNotifications = new ArrayList<>();

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

		extensionToLanguage = queryConfiguration().extensionToLanguage();
	}

	@SneakyThrows
	private <T> T call(String action, Object body, TypeReference<T> responseType) {
		final var bodyString = objectMapper.writeValueAsString(body);
		System.err.println("lsp: %s: >> %s".formatted(action, bodyString));

		var request = HttpRequest.newBuilder(URI.create(url + "/" + action))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(bodyString)).build();

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException(response.body());
		}

		final var responseBody = response.body();
		System.err.println("lsp: %s: << %s".formatted(action, responseBody));

		if (responseType == null) {
			return null;
		}

		return objectMapper.readValue(responseBody, responseType);
	}

	private <T> T request(String method, Object params, TypeReference<T> responseType) {
		return call("request", new RequestPayload(method, params), responseType);
	}

	private <T> T notify(String method, Object params, TypeReference<T> responseType) {
		return call("notify", new RequestPayload(method, params), responseType);
	}

	public Configuration queryConfiguration() {
		return call("configuration", Map.of(), new TypeReference<Configuration>() {});
	}

	public Location queryDefinition(Path path, int line, int character) {
		path = toCorrectedPath(path);
		notifyDocumentDidOpenIfNecessary(path);

		final var textDocument = new TextDocument(path);
		final var position = new Position(line - 1, character - 1);

		return request("textDocument/definition", new QueryDefinitionBody(textDocument, position), new TypeReference<Location>() {});
	}

	public List<Location> queryReferences(Path path, int line, int character) {
		path = toCorrectedPath(path);
		notifyDocumentDidOpenIfNecessary(path);

		final var textDocument = new TextDocument(path);
		final var position = new Position(line - 1, character - 1);
		final var context = new Context(true);

		return request("textDocument/references", new QueryReferencesBody(textDocument, position, context), new TypeReference<List<Location>>() {});
	}

	private boolean notifyDocumentDidOpenIfNecessary(Path path) {
		final var version = fileVersions.get(path);
		if (version != null) {
			return false;
		}

		notifyDocumentDidOpen(path);
		return true;
	}

	@SneakyThrows
	private void notifyDocumentDidOpen(Path path) {
		path = toCorrectedPath(path);

		final var content = Files.readString(path, StandardCharsets.UTF_8);
		final var version = fileVersions.compute(path, increment);

		notifyDocumentDidOpen(path, version, content);
	}

	private void notifyDocumentDidOpen(Path path, int version, String text) {
		final var textDocument = new TextDocument(
			path,
			getLanguageFromExtension(path),
			version,
			text
		);

		notify("textDocument/didOpen", new NotifyDidOpenBody(textDocument), null);
	}

	public void onFileWritten(Path path, String content) {
		final var correctedPath = toCorrectedPath(path);

		fetchNotifications(Duration.ZERO);
		pendingNotifications.removeIf((notification) -> !isDiagnosticFor(notification, correctedPath, null));

		final var version = notifyDocumentDidChange(correctedPath, content);

		final var extension = getExtensionWithDot(correctedPath);
		if (extensionToLanguage.containsKey(extension)) {
			waitForDiagnostics(correctedPath, version, Duration.ofSeconds(5));
		}
	}

	private int notifyDocumentDidChange(Path path, String text) {
		final var correctedPath = toCorrectedPath(path);

		final var version = fileVersions.compute(correctedPath, increment);
		if (version == 1) {
			notifyDocumentDidOpen(correctedPath, version, text);
			return version;
		}

		final var textDocument = new TextDocument(correctedPath, version);
		final var contentChanges = List.of(new ContentChange(text));

		notify("textDocument/didChange", new NotifyDidChangedBody(textDocument, contentChanges), null);
		return version;
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

	private List<Notification> fetchNotifications(Duration timeout) {
		final var batch = call("notifications", new FetchNotifications(notificationCursor, timeout), new TypeReference<NotificationBatch>() {});

		notificationCursor = batch.cursor();
		pendingNotifications.addAll(batch.notifications());

		return batch.notifications();
	}

	private boolean isDiagnosticFor(Notification notification, Path correctedPath, Integer version) {
		if (!(notification instanceof Notification.PublishDiagnostics publishDiagnostics)) {
			return false;
		}

		if (!publishDiagnostics.path().equals(correctedPath)) {
			return false;
		}

		final var notificationVersion = publishDiagnostics.version();
		return version == null || notificationVersion == null || version.equals(notificationVersion);
	}

	@SneakyThrows
	private void waitForDiagnostics(Path correctedPath, int version, Duration timeout) {
		final var startTime = System.currentTimeMillis();
		final var endTime = startTime + timeout.toMillis();

		while (true) {
			final var remaining = endTime - System.currentTimeMillis();
			if (remaining <= 0) {
				break;
			}

			final var notifications = fetchNotifications(Duration.ofMillis(remaining));
			if (notifications.stream().anyMatch((notification) -> isDiagnosticFor(notification, correctedPath, version))) {
				return;
			}

			if (notifications.isEmpty()) {
				Thread.sleep(Duration.ofMillis(50));
			}
		}

		throw new IllegalStateException("Timed out waiting for diagnostics for %s at version %d".formatted(correctedPath, version));
	}

	public String collectDiagnostics() {
		fetchNotifications(Duration.ZERO);

		final var notifications = new ArrayList<>(pendingNotifications);
		pendingNotifications.clear();

		final var diagnosticsPerUri = notifications.stream()
			.filter((notification) -> notification instanceof Notification.PublishDiagnostics)
			.map(Notification.PublishDiagnostics.class::cast)
			.filter((notification) -> {
				final var version = notification.version();
				final var path = notification.path();

				return version == null || version.equals(fileVersions.get(path));
			})
			.filter((notification) -> !notification.diagnostics().isEmpty())
			.collect(Collectors.toMap(
				Notification.PublishDiagnostics::path,
				Notification.PublishDiagnostics::diagnostics,
				(_, b) -> b) /* last one wins */
			);

		if (diagnosticsPerUri.isEmpty()) {
			return null;
		}

		final var builder = new StringBuilder();
		builder.append("<new-diagnostics>The following new diagnostic issues were detected:\n");

		final var currentPath = Path.of(".").toAbsolutePath().normalize().toString().length() + 1;

		for (var entry : diagnosticsPerUri.entrySet()) {
			final var path = entry.getKey();
			final var diagnostics = entry.getValue();

			builder.append("\n").append(path.toString().substring(currentPath)).append(":");
			for (final var diagnostic : diagnostics) {
				final var location = diagnostic.range() != null
					? "[Line %s]".formatted(diagnostic.range().start().toString1Based())
					: "[File]";

				builder.append("\n  ").append(location).append(": ").append(diagnostic.message());
			}
		}

		builder.append("</new-diagnostics>");

		return builder.toString();
	}

	private record Configuration(Map<String, String> extensionToLanguage) {}

	private record TextDocument(String uri, @JsonInclude(NON_NULL) String languageId, @JsonInclude(NON_NULL) Integer version, @JsonInclude(NON_NULL) String text) {

		TextDocument(Path path) {
			this(toUri(path), null, null, null);
		}

		TextDocument(Path path, int version) {
			this(toUri(path), null, version, null);
		}

		TextDocument(Path path, String languageId, int version, String text) {
			this(toUri(path), languageId, version, text);
		}

	}

	private record Context(boolean includeDeclaration) {}

	private record QueryDefinitionBody(TextDocument textDocument, Position position) {}

	private record QueryReferencesBody(TextDocument textDocument, Position position, Context context) {}

	private record NotifyDidOpenBody(TextDocument textDocument) {}

	private record NotifyDidChangedBody(TextDocument textDocument, List<ContentChange> contentChanges) {}

	private record FetchNotifications(int after, @JsonProperty("timeoutMs") int timeout) {

		FetchNotifications(int after, Duration timeout) {
			this(after, (int) timeout.toSeconds());
		}

	}

	private record ContentChange(String text) {}

	private record RequestPayload(String method, Object params) {}

	private static Path toCorrectedPath(Path path) {
		return path.toAbsolutePath().normalize();
	}

	private static String toUri(Path path) {
		return path.toUri().toString();
	}

}