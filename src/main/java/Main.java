import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
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

	@JsonClassDescription("Write content to a file")
	static class Write {

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

	@JsonClassDescription("Execute a shell command")
	static class Bash {

		@JsonProperty("command")
		@JsonPropertyDescription("The command to execute")
		public String command;

		public String execute() {
			Path logDirectoryPath = null;
			try {
				logDirectoryPath = Files.createTempDirectory("claude-code-bash");

				final var outputLogFile = new File(logDirectoryPath.toFile(), "output.log");
				final var errorLogFile = new File(logDirectoryPath.toFile(), "error.log");

				final var process = new ProcessBuilder(command)
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
			.addTool(Write.class)
			.addTool(Bash.class)
			.addUserMessage(prompt);

		while (true) {
			final var choices = client.chat().completions().create(createParamsBuilder.build()).choices();
			if (choices.isEmpty()) {
				throw new IllegalStateException("no choices returned from the chat completion");
			}

			final var choice = choices.getFirst();

			final var message = choice.message();
			createParamsBuilder.addMessage(message);

			if (FinishReason.TOOL_CALLS.equals(choice.finishReason())) {
				final var toolCalls = message.toolCalls();
				if (toolCalls.isEmpty()) {
					throw new IllegalStateException("no tool calls found in the message");
				}

				for (final var toolCall : toolCalls.get()) {
					if (!toolCall.isFunction()) {
						throw new IllegalStateException("unexpected tool call type: " + toolCall.toString());
					}

					final var functionToolCall = toolCall.asFunction();
					final var result = callFunction(functionToolCall.function());

					createParamsBuilder.addMessage(
						ChatCompletionToolMessageParam.builder()
							.toolCallId(functionToolCall.id())
							.contentAsJson(result)
							.build()
					);
				}
			} else if (FinishReason.STOP.equals(choice.finishReason())) {
				message.content().ifPresent(System.out::println);
				break;
			} else {
				System.err.println("Chat completion finished with reason: " + choice.finishReason());
				break;
			}
		}
	}

	static Object callFunction(ChatCompletionMessageFunctionToolCall.Function function) {
		System.err.println("[tool] calling %s with arguments %s".formatted(function.name(), function.arguments()));

		switch (function.name()) {
			case "Read":
				return function.arguments(Read.class).execute();
			case "Write":
				return function.arguments(Write.class).execute();
			case "Bash":
				return function.arguments(Bash.class).execute();
			default:
				throw new IllegalArgumentException("Unknown function: " + function.name());
		}
	}

}