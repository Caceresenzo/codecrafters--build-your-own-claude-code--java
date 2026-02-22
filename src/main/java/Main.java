import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class Main {

	@JsonClassDescription("Read and return the contents of a file")
	static class Read {

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

	public static void main(String[] args) {
		String prompt = null;
		for (int i = 0; i < args.length; i++) {
			if ("-p".equals(args[i]) && i + 1 < args.length) {
				prompt = args[i + 1];
			}
		}

		if (prompt == null || prompt.isEmpty()) {
			throw new RuntimeException("error: -p flag is required");
		}

		String apiKey = System.getenv("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isEmpty()) {
			throw new RuntimeException("OPENROUTER_API_KEY is not set");
		}

		String baseUrl = System.getenv("OPENROUTER_BASE_URL");
		if (baseUrl == null || baseUrl.isEmpty()) {
			baseUrl = "https://openrouter.ai/api/v1";
		}

		String modelName = System.getenv("OPENROUTER_MODEL_NAME");
		if (modelName == null || modelName.isEmpty()) {
			modelName = "anthropic/claude-haiku-4.5"; // z-ai/glm-4.5-air:free
		}

		OpenAIClient client = OpenAIOkHttpClient.builder()
			.apiKey(apiKey)
			.baseUrl(baseUrl)
			.build();

		ChatCompletion response = client.chat().completions().create(
			ChatCompletionCreateParams.builder()
				.model(modelName)
				.addTool(Read.class)
				.addUserMessage(prompt)
				.build()
		);

		if (response.choices().isEmpty()) {
			throw new RuntimeException("no choices in response");
		}

		System.out.print(response.choices().get(0).message().content().orElse(""));
	}

}