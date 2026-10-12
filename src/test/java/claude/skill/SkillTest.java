package claude.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class SkillTest {

	@Test
	void testFormatAll() {
		final var skill = new Skill(null, "test", "test", null, "echo $ARGUMENTS");
		final var formatted = skill.format(List.of("arg1", "arg2"), false);

		assertEquals("echo arg1 arg2", formatted);
	}

	@Test
	void testFormatDollar() {
		final var skill = new Skill(null, "test", "test", null, "echo $0 $1");
		final var formatted = skill.format(List.of("arg1", "arg2"), false);

		assertEquals("echo arg1 arg2", formatted);
	}

}