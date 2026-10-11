package claude.tool;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import claude.skill.Skillbox;

@JsonClassDescription("Load a skill's instructions into the conversation")
public class Skill {

	@JsonProperty("name")
	@JsonPropertyDescription("The name of the skill to use")
	public String name;

	@JsonProperty("args")
	@JsonPropertyDescription("Optional arguments for the skill")
	public Optional<String> argumentsString;

	public String execute(Skillbox skillbox) {
		final var skill = skillbox.find(name);
		if (skill.isEmpty()) {
			return "Skill not found: %s".formatted(name);
		}

		final var arguments = argumentsString.isPresent()
			? Arrays.asList(argumentsString.get().split(" "))
			: Collections.<String>emptyList();

		return skill.get().format(arguments, true);
	}

}