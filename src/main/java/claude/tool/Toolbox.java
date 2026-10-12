package claude.tool;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.Getter;

@Getter
public class Toolbox {

	private final Map<String, Class<? extends Tool>> classByName = new HashMap<>();

	public void add(Class<? extends Tool> toolClass) {
		var name = toolClass.getSimpleName();

		final var jsonTypeName = toolClass.getAnnotation(JsonTypeName.class);
		if (jsonTypeName != null) {
			name = jsonTypeName.value();
		}

		classByName.put(name, toolClass);
	}

	public Optional<Class<? extends Tool>> find(String name) {
		return Optional.ofNullable(classByName.get(name));
	}

}