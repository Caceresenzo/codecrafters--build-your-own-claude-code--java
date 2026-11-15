package claude.tool.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import claude.Agent;
import claude.tool.Tool;

@JsonTypeName("Write")
@JsonClassDescription("WriteTool content to a file")
public class WriteTool extends Tool {

	@JsonProperty("file_path")
	@JsonPropertyDescription("The path of the file to write to")
	public String filePath;

	@JsonProperty("content")
	@JsonPropertyDescription("The content to write to the file")
	public String content;

	@Override
	public String execute(Agent agent) {
		final var path = Path.of(filePath).toAbsolutePath().normalize();
		final var bytes = content.getBytes();

		try {
			Files.write(path, bytes);
		} catch (IOException exception) {
			return "The file could not be written: %s".formatted(exception.getMessage());
		}

		final var lspClient = agent.getLspClient();
		if (lspClient != null) {
			lspClient.onFileWritten(path, content);
		}

		final var builder = new StringBuilder();
		builder.append("Wrote ").append(bytes.length).append(" bytes");

		if (lspClient != null) {
			final var diagnostics = lspClient.collectDiagnostics();

			if (diagnostics != null) {
				builder.append("\n\n").append(diagnostics);
			}
		}

		return builder.toString();
	}

}