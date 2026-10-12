package claude;

import java.util.ArrayList;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

import claude.skill.Skillbox;
import claude.tool.Toolbox;
import lombok.Getter;

@Getter
public class Agent {

	private final String name;

	private final OpenAIClient client;
	private final String modelName;
	private final ChatCompletionCreateParams.Builder history;

	private final Skillbox skillbox;
	private final Toolbox toolbox;

	public Agent(String name, OpenAIClient client, String modelName, Toolbox toolbox, Skillbox skillbox, boolean includeSkillsMessage) {
		this.name = name;

		this.client = client;
		this.modelName = modelName;

		this.skillbox = skillbox;
		this.toolbox = toolbox;

		this.history = ChatCompletionCreateParams.builder()
			.model(modelName);

		for (final var toolClass : toolbox.getClassByName().values()) {
			this.history.addTool(toolClass);
		}

		if (includeSkillsMessage) {
			this.history.addSystemMessage(skillbox.toSystemPrompt());
		}
	}

	public Agent fork(String name, boolean includeSkillsMessage) {
		return new Agent(
			name,
			client,
			modelName,
			toolbox,
			skillbox,
			includeSkillsMessage
		);
	}

	public void pushUserInput(String prompt) {
		if (!prompt.startsWith("/")) {
			pushMessage(prompt);
			return;
		}

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

				pushMessage(prompt);
			} else {
				final var formatted = skill.format(skillArguments, true);

				pushMessage(formatted);
			}
		}
	}

	public void pushMessage(String message) {
		history.addUserMessage(message);
	}

	public Optional<String> loop() {
		while (true) {
			final var choices = client.chat().completions().create(history.build()).choices();
			if (choices.isEmpty()) {
				throw new IllegalStateException("no choices returned from the chat completion");
			}

			final var choice = choices.getFirst();

			final var message = choice.message();
			history.addMessage(message);

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
					final var result = callFunction(functionToolCall.function(), skillbox);

					history.addMessage(
						ChatCompletionToolMessageParam.builder()
							.toolCallId(functionToolCall.id())
							.contentAsJson(result)
							.build()
					);
				}
			} else if (FinishReason.STOP.equals(choice.finishReason())) {
				return message.content();
			} else {
				System.err.println("Chat completion finished with reason: " + choice.finishReason());
				return Optional.empty();
			}
		}
	}

	private Object callFunction(ChatCompletionMessageFunctionToolCall.Function function, Skillbox skillbox) {
		System.err.println("[tool] calling %s with arguments %s".formatted(function.name(), function.arguments()));

		final var toolClass = toolbox.find(function.name()).orElseThrow(() -> new IllegalArgumentException("Unknown function: " + function.name()));
		final var result = function.arguments(toolClass).execute(this);

		System.err.println("[tool] result: %s".formatted(result));

		return result;
	}

}