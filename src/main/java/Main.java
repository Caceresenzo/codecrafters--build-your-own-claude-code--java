import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

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

		var apiKey = System.getenv("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isEmpty()) {
			throw new RuntimeException("OPENROUTER_API_KEY is not set");
		}

		var baseUrl = System.getenv("OPENROUTER_BASE_URL");
		if (baseUrl == null || baseUrl.isEmpty()) {
			baseUrl = "https://openrouter.ai/api/v1";
		}

		var modelName = System.getenv("OPENROUTER_MODEL_NAME");
		if (modelName == null || modelName.isEmpty()) {
			modelName = "anthropic/claude-haiku-4.5"; // z-ai/glm-4.5-air:free
		}

		final var client = OpenAIOkHttpClient.builder()
			.apiKey(apiKey)
			.baseUrl(baseUrl)
			.build();

		final var createParamsBuilder = ChatCompletionCreateParams.builder()
			.model(modelName)
			.addTool(Read.class)
			.addUserMessage(prompt);

		final var running = new boolean[] { true };
		while (running[0]) {
			running[0] = false;

			client.chat().completions().create(createParamsBuilder.build()).choices()
				.stream()
				.map(ChatCompletion.Choice::message)
				.peek(createParamsBuilder::addMessage) /* bad practice */
				.flatMap((message) -> {
					message.content().ifPresent(System.out::println);
					return message.toolCalls().stream().flatMap(Collection::stream);
				})
				.forEach((toolCall) -> {
					final var functionToolCall = toolCall.asFunction();

					final var result = callFunction(functionToolCall.function());

					createParamsBuilder.addMessage(ChatCompletionToolMessageParam.builder()
						.toolCallId(functionToolCall.id())
						.contentAsJson(result)
						.build());

					running[0] = true;
				});
		}
	}

	static Object callFunction(ChatCompletionMessageFunctionToolCall.Function function) {
		System.err.println("[tool] calling %s with arguments %s".formatted(function.name(), function.arguments()));

		switch (function.name()) {
			case "Read":
				return function.arguments(Read.class).execute();
			default:
				throw new IllegalArgumentException("Unknown function: " + function.name());
		}
	}

}