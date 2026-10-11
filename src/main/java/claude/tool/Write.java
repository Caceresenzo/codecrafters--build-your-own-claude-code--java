package claude.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Write content to a file")
public class Write {

	@JsonProperty("file_path")
	@JsonPropertyDescription("The path of the file to write to")
	public String filePath;

	@JsonProperty("content")
	@JsonPropertyDescription("The content to write to the file")
	public String content;

	public String execute() {
		try {
			final var bytes = content.getBytes();
			Files.write(Path.of(filePath), bytes);
			return "Wrote %s bytes".formatted(bytes.length);
		} catch (IOException exception) {
			return "The file could not be written: %s".formatted(exception.getMessage());
		}
	}

}