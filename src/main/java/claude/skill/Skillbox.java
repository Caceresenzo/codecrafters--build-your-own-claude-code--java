package claude.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.SneakyThrows;

public class Skillbox {

	public static final String ROOT_DIRECTORY = ".claude/skills/";
	public static final String FILE_NAME = "SKILL.md";
	public static final String METADATA_SEPARATOR = "---";

	private final List<Skill> skills = new ArrayList<>();

	public String toSystemPrompt() {
		final var builder = new StringBuilder();
		builder.append("You have access to the following skills:\n\n");

		for (final var skill : skills) {
			builder.append("- ").append(skill.name()).append(": ").append(skill.description()).append("\n");
		}

		return builder.toString();
	}

	public Optional<Skill> find(String name) {
		return skills.stream()
			.filter((skill) -> skill.name().equalsIgnoreCase(name))
			.findFirst();
	}

	@SneakyThrows
	public void locate() {
		skills.clear();

		final var root = Path.of(ROOT_DIRECTORY);
		if (!Files.exists(root)) {
			return;
		}

		try (final var directoryStream = Files.list(root)) {
			final var directories = directoryStream.filter(Files::isDirectory).toList();

			for (final var directory : directories) {
				final var skillFile = directory.resolve(FILE_NAME);
				if (!Files.exists(skillFile)) {
					continue;
				}

				final var lines = Files.readAllLines(skillFile);
				if (!METADATA_SEPARATOR.equals(lines.removeFirst())) {
					continue;
				}

				final var builder = Skill.builder()
					.directory(directory)
					.context(SkillContext.DEFAULT);

				String line;
				while (!METADATA_SEPARATOR.equals(line = lines.removeFirst())) {
					final var parts = line.split(":", 2);
					if (parts.length != 2) {
						continue;
					}

					final var key = parts[0].trim();
					final var value = parts[1].trim();

					if ("name".equalsIgnoreCase(key)) {
						builder.name(value);
					} else if ("description".equalsIgnoreCase(key)) {
						builder.description(value);
					} else if ("context".equalsIgnoreCase(key)) {
						builder.context(SkillContext.valueOf(value.toUpperCase()));
					}
				}

				final var content = String.join("\n", lines).trim();
				builder.content(content);

				final var skill = builder.build();
				skills.add(skill);
			}
		}
	}

}