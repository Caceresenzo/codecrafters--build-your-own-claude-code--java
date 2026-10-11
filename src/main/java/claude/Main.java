package claude;

import java.util.ArrayList;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

import claude.skill.Skillbox;
import claude.tool.Bash;
import claude.tool.Read;
import claude.tool.Write;

public class Main {

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
			modelName = "anthropic/claude-haiku-4.5"; // nvidia/nemotron-3.5-lightning:free
		}

		final var client = OpenAIOkHttpClient.builder()
			.apiKey(apiKey)
			.baseUrl(baseUrl)
			.build();

		final var skillbox = new Skillbox();
		skillbox.locate();

		System.err.println(skillbox.toSystemPrompt());

		final var createParamsBuilder = ChatCompletionCreateParams.builder()
			.model(modelName)
			.addTool(Read.class)
			.addTool(Write.class)
			.addTool(Bash.class)
			.addSystemMessage(skillbox.toSystemPrompt());

		if (prompt.startsWith("/")) {
			final var arguments = prompt.split(" ");

			final var skillNames = new ArrayList<String>(arguments.length);
			final var skillArguments = new ArrayList<String>(arguments.length);

			for (final var argument : arguments) {
				if (argument.startsWith("/")) {
					skillNames.add(argument.substring(1));
				} else {
					skillArguments.add(argument);
				}
			}

			for (final var skillName : skillNames) {
				final var skill = skillbox.find(skillName).orElse(null);

				if (skill == null) {
					System.err.println("skill: %s: not found".formatted(skillName));

					createParamsBuilder
						.addUserMessage(prompt);
				} else {
					final var formatted = skill.format(skillArguments);

					createParamsBuilder
						.addUserMessage("User invoked a Skill: %s%n%n%s".formatted(skill.name(), formatted));
				}
			}

		} else {
			createParamsBuilder
				.addUserMessage(prompt);
		}

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

		final var result = switch (function.name()) {
			case "Read" -> function.arguments(Read.class).execute();
			case "Write" -> function.arguments(Write.class).execute();
			case "Bash" -> function.arguments(Bash.class).execute();
			default -> throw new IllegalArgumentException("Unknown function: " + function.name());
		};

		System.err.println("[tool] result: %s".formatted(result));

		return result;
	}

}