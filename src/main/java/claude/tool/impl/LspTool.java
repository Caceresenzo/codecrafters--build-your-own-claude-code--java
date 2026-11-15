package claude.tool.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import claude.Agent;
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
		final var content = Files.readString(path, StandardCharsets.UTF_8);

		lspClient.notifyDocumentDidOpen(path, 1, content);

		final var definition = lspClient.queryDefinition(path, line, character);

		return "Defined in %s, from %s to %s".formatted(definition.uri(), definition.range().start().toString1Based(), definition.range().end().toString1Based());
	}

	public String executeFindReferences(LspClient lspClient, Path path) {
		return "LSP is not implemented yet";
	}

}