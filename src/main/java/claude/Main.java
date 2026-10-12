package claude;

import com.openai.client.okhttp.OpenAIOkHttpClient;

import claude.skill.Skillbox;
import claude.tool.Toolbox;
import claude.tool.impl.BashTool;
import claude.tool.impl.ReadTool;
import claude.tool.impl.SkillTool;
import claude.tool.impl.WriteTool;

public class Main {

	public static void main(String[] args) {
		String prompt = null;
		for (int i = 0; i < args.length; i++) {
			if ("-p".equals(args[i]) && i + 1 < args.length) {
				prompt = args[i + 1];
			}
		}

		if (prompt == null || prompt.isEmpty()) {
			throw new RuntimeException("error: -p flag is required");
		}

		var apiKey = System.getenv("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isEmpty()) {
			throw new RuntimeException("OPENROUTER_API_KEY is not set");
		}

		var baseUrl = System.getenv("OPENROUTER_BASE_URL");
		if (baseUrl == null || baseUrl.isEmpty()) {
			baseUrl = "https://openrouter.ai/api/v1";
		}

		var modelName = System.getenv("OPENROUTER_MODEL_NAME");
		if (modelName == null || modelName.isEmpty()) {
			modelName = "anthropic/claude-haiku-4.5"; // nvidia/nemotron-3.5-lightning:free
		}

		final var client = OpenAIOkHttpClient.builder()
			.apiKey(apiKey)
			.baseUrl(baseUrl)
			.build();

		final var skillbox = new Skillbox();
		skillbox.locate();

		final var toolbox = new Toolbox();
		toolbox.add(ReadTool.class);
		toolbox.add(WriteTool.class);
		toolbox.add(BashTool.class);
		toolbox.add(SkillTool.class);

		final var claude = new Agent(
			"Claude",
			client,
			modelName,
			toolbox,
			skillbox,
			true
		);

		claude.loop().ifPresent(System.out::println);
	}

}