package claude.tool.impl.lsp;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import claude.Agent;
import claude.tool.Tool;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeName("LSP")
@JsonClassDescription("Returns the file, line, and character where a symbol is defined.")
public class LspTool extends Tool {

	@JsonProperty("operation")
	@Schema(allowableValues = { "goToDefinition", "findReferences" })
	public String command;

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
		return "LSP is not implemented yet";
	}

}