package claude.tool.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import claude.Agent;
import claude.skill.SkillContext;
import claude.tool.Tool;

@JsonTypeName("Skill")
@JsonClassDescription("Load a skill's instructions into the conversation")
public class SkillTool extends Tool {

	@JsonProperty("name")
	@JsonPropertyDescription("The name of the skill to use")
	public String name;

	@JsonProperty("args")
	@JsonPropertyDescription("Optional arguments for the skill")
	public Optional<String> argumentsString;

	@Override
	public String execute(Agent agent) {
		final var skill = agent.getSkillbox().find(name).orElse(null);
		if (skill == null) {
			return "SkillTool not found: %s".formatted(name);
		}

		final var arguments = argumentsString.isPresent()
			? Arrays.asList(argumentsString.get().split(" "))
			: Collections.<String>emptyList();

		final var formatted = skill.format(arguments, true);

		if (SkillContext.FORK.equals(skill.context())) {
			final var child = agent.fork("sub-agent", false);
			child.pushMessage(formatted);

			final var response = child.loop();
			if (response.isPresent()) {
				return "Sub-agent response: %s".formatted(response.get());
			}

			return "Sub-agent did not return a response";
		}

		return formatted;
	}

}