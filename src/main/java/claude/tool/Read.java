package claude.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Read and return the contents of a file")
public class Read {

	@JsonProperty("file_path")
	@JsonPropertyDescription("The path to the file to read")
	public String filePath;

	public String execute() {
		try {
			return Files.readString(Path.of(filePath));
		} catch (IOException exception) {
			return "The file could not be read: %s".formatted(exception.getMessage());
		}
	}

}