package claude.skill;

import java.nio.file.Path;

import lombok.Builder;

@Builder
public record Skill(
	Path directory,
	String name,
	String description,
	String content
) {}