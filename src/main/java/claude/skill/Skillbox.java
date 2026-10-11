package claude.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.SneakyThrows;

public class Skillbox {

	private final List<Skill> skills = new ArrayList<>();

	public String toSystemPrompt() {
		final var builder = new StringBuilder();
		builder.append("You have access to the following skills:\n\n");

		for (final var skill : skills) {
			builder.append("- ").append(skill.name()).append(": ").append(skill.description()).append("\n");
		}

		return builder.toString();
	}

	@SneakyThrows
	public void locate() {
		skills.clear();

		final var root = Path.of(".claude/skills/");
		if (!Files.exists(root)) {
			return;
		}

		try (final var directoryStream = Files.list(root)) {
			final var directories = directoryStream.filter(Files::isDirectory).toList();

			for (final var directory : directories) {
				final var skillFile = directory.resolve("SKILL.md");
				if (!Files.exists(skillFile)) {
					continue;
				}

				final var lines = Files.readAllLines(root);
				if (lines.removeFirst() != "---") {
					continue;
				}

				final var builder = Skill.builder()
					.directory(directory);

				String line;
				while ((line = lines.removeFirst()) != "---") {
					final var parts = line.split(":", 1);
					if (parts.length != 2) {
						continue;
					}

					final var key = parts[0].trim();
					final var value = parts[1].trim();

					if ("name".equalsIgnoreCase(key)) {
						builder.name(value);
					} else if ("description".equalsIgnoreCase(key)) {
						builder.description(value);
					}
				}

				final var skill = builder.build();
				skills.add(skill);
			}
		}
	}

}