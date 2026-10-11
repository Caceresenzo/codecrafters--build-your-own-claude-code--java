package claude.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import lombok.Builder;

@Builder
public record Skill(
	Path directory,
	String name,
	String description,
	String content
) {

	public static final String ALL_ARGUMENTS = "$ARGUMENTS";
	public static final Pattern ARGUMENT_PATTERN = Pattern.compile("(?:\\$(\\d+)|\\" + ALL_ARGUMENTS + "\\[(\\d+)\\])");

	public String format(List<String> arguments) {
		var formatted = ARGUMENT_PATTERN.matcher(content).replaceAll((match) -> {
			final var index = Integer.parseInt(match.group(1) != null ? match.group(1) : match.group(2));

			if (index < arguments.size()) {
				return arguments.get(index);
			}

			return "";
		});

		if (formatted.contains(ALL_ARGUMENTS)) {
			formatted = formatted.replace(ALL_ARGUMENTS, String.join(" ", arguments));
		}

		return formatted;
	}

}