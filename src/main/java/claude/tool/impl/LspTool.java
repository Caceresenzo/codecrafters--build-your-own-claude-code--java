package claude.tool.impl;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import claude.Agent;
import claude.lsp.Location;
import claude.lsp.LspClient;
import claude.tool.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.SneakyThrows;

@JsonTypeName("LSP")
@JsonClassDescription("Returns the file, line, and character where a symbol is defined.")
public class LspTool extends Tool {

	public static final String GO_TO_DEFINITION = "goToDefinition";
	public static final String FIND_REFERENCES = "findReferences";

	@JsonProperty("operation")
	@Schema(allowableValues = { GO_TO_DEFINITION, FIND_REFERENCES })
	public String operation;

	@JsonProperty("filePath")
	@JsonPropertyDescription("Absolute path, or a path relative to the agent's current working directory.")
	public String filePath;

	@JsonProperty("line")
	@JsonPropertyDescription("1-based line number of the symbol.")
	@Schema(minimum = "1")
	public int line;

	@JsonProperty("character")
	@JsonPropertyDescription("1-based character offset in UTF-16 code units (same as character count for ASCII).")
	@Schema(minimum = "1")
	public int character;

	@Override
	public String execute(Agent agent) {
		final var lspClient = agent.getLspClient();
		if (lspClient == null) {
			return "LSP client is not configured.";
		}

		final var path = Path.of(filePath).toAbsolutePath().normalize();
		if (!Files.exists(path)) {
			return "File does not exist: " + path;
		}

		return switch (operation) {
			case GO_TO_DEFINITION -> executeGoToDefinition(lspClient, path);
			case FIND_REFERENCES -> executeFindReferences(lspClient, path);
			default -> "LSP is not implemented yet";
		};
	}

	@SneakyThrows
	public String executeGoToDefinition(LspClient lspClient, Path path) {
		lspClient.notifyDocumentDidOpen(path);

		final var definition = lspClient.queryDefinition(path, line, character);

		return "Defined in %s, from %s to %s".formatted(definition.uri(), definition.range().start().toString1Based(), definition.range().end().toString1Based());
	}

	public String executeFindReferences(LspClient lspClient, Path path) {
		lspClient.notifyDocumentDidOpen(path);

		final var allLocations = lspClient.queryReferences(path, line, character);
		if (allLocations.isEmpty()) {
			return "No reference found.";
		}

		final var locationsPerUri = allLocations.stream().collect(Collectors.groupingBy(Location::uri));

		final var builder = new StringBuilder();
		builder
			.append("Found ")
			.append(allLocations.size())
			.append(" references across ")
			.append(locationsPerUri.size())
			.append(" files:\n");

		final var currentPath = Path.of(".").toAbsolutePath().normalize().toString().length();

		for (final var entry : locationsPerUri.entrySet()) {
			final var uri = Path.of(URI.create(entry.getKey()));
			final var locations = entry.getValue();

			builder
				.append("\n")
				.append(uri.toString().substring(currentPath))
				.append(":\n");

			for (final var location : locations) {
				builder
					.append("  Line ")
					.append(location.range().start().toString1Based())
					.append("\n");
			}
		}

		return builder.toString();
	}

}