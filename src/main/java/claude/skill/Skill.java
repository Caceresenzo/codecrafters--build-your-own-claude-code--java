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
		var formatted = content;

		ARGUMENT_PATTERN.matcher(formatted).replaceAll((match) -> {
			final var index = Integer.parseInt(match.group(1) != null ? match.group(1) : match.group(2));

			if (index < 0 || index >= arguments.size()) {
				return match.group();
			}

			return "";
		});

		if (formatted.contains(ALL_ARGUMENTS)) {
			formatted = formatted.replace(ALL_ARGUMENTS, formatted);
		}

		return formatted;
	}

}