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

@JsonTypeName("Read")
@JsonClassDescription("ReadTool and return the contents of a file")
public class ReadTool extends Tool {

	@JsonProperty("file_path")
	@JsonPropertyDescription("The path to the file to read")
	public String filePath;

	@Override
	public String execute(Agent agent) {
		try {
			return Files.readString(Path.of(filePath));
		} catch (IOException exception) {
			return "The file could not be read: %s".formatted(exception.getMessage());
		}
	}

}