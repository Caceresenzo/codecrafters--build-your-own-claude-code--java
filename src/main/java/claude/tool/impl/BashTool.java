package claude.tool.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import claude.Agent;
import claude.tool.Tool;

@JsonTypeName("Bash")
@JsonClassDescription("Execute a shell command")
public class BashTool extends Tool {

	@JsonProperty("command")
	@JsonPropertyDescription("The command to execute")
	public String command;

	@Override
	public String execute(Agent agent) {
		Path logDirectoryPath = null;
		try {
			logDirectoryPath = Files.createTempDirectory("claude-code-bash");

			final var outputLogFile = new File(logDirectoryPath.toFile(), "output.log");
			final var errorLogFile = new File(logDirectoryPath.toFile(), "error.log");

			final var process = new ProcessBuilder("/bin/sh", "-c", command)
				.redirectOutput(outputLogFile)
				.redirectError(errorLogFile)
				.start();

			final var exitCode = process.waitFor();

			return """
				Executed command: %s
				Exit Code: %s

				--- output log ---
				%s

				--- error log ---
				%s
				""".formatted(
				command,
				exitCode,
				Files.readString(outputLogFile.toPath()),
				Files.readString(errorLogFile.toPath())
			);
		} catch (IOException | InterruptedException exception) {
			return "The file could not be written: %s".formatted(exception.getMessage());
		} finally {
			deleteLogFiles(logDirectoryPath);
		}
	}

	private void deleteLogFiles(Path logDirectoryPath) {
		if (logDirectoryPath == null) {
			return;
		}

		try {
			Files.deleteIfExists(logDirectoryPath.resolve("output.log"));
		} catch (IOException __) {}

		try {
			Files.deleteIfExists(logDirectoryPath.resolve("error.log"));
		} catch (IOException __) {}

		try {
			Files.deleteIfExists(logDirectoryPath);
		} catch (IOException __) {}
	}

}