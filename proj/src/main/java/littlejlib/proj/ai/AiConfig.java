package littlejlib.proj.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import littlejlib.proj.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code ai.toml} in proj's folder: the AI providers proj may use, tried in order until one answers. Written with
 * commented defaults the first time it is needed; nothing about a provider is fixed in code. AI is only used when
 * the user asks for it or approves it (PRP 001Q), or to suggest names that the user then edits (PRP 001R).
 */
public final class AiConfig {

    /** One provider. {@code type}: "anthropic" (any server speaking the Claude Messages API) or "command" (a program reading the prompt on stdin). */
    public static final class Provider {
        public String name;
        public String type = "anthropic";
        public String url;
        public String model;
        /** The key itself (keep this file private) or, better, the name of an environment variable holding it. */
        public String apiKey;
        public String apiKeyEnv;
        public List<String> command;
        public boolean enabled = true;
        public Integer timeoutSeconds;

        @Override
        public String toString() {
            return name + " (" + ("command".equals(type) ? String.join(" ", command == null ? List.of() : command) : url + " " + model) + ")";
        }
    }

    public int timeoutSeconds = 120;
    public int maxTokens = 4000;
    /** A file with the prompt that names PRPs; empty uses the one shipped in proj. */
    public String shortNamesPrompt = "";
    /** A tab-separated file of short forms (form, meaning, score) added to that prompt; empty for none. */
    public String shortForms = "";
    public List<Provider> provider = new ArrayList<>();

    public static Path file() {
        return AppPaths.home().resolve("ai.toml");
    }

    public static AiConfig load() throws IOException {
        var f = file();
        if (!Files.isRegularFile(f)) {
            Files.createDirectories(f.getParent());
            Files.writeString(f, DEFAULT, StandardCharsets.UTF_8);
        }
        var mapper = TomlMapper.builder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
        return mapper.readValue(Files.readString(f, StandardCharsets.UTF_8), AiConfig.class);
    }

    static final String DEFAULT = """
            # proj's AI providers, tried in order until one answers. proj uses AI only when you ask for it or
            # approve it, and to suggest names you can change. Nothing here is fixed in code: edit freely.
            #
            # type = "anthropic": any server speaking the Claude Messages API (POST <url>/v1/messages), e.g. the
            #        Anthropic API itself or a local proxy. Key: apiKeyEnv names an environment variable (preferred),
            #        or apiKey holds the key (then keep this file private).
            # type = "command": a program that reads the prompt on stdin and prints the answer, e.g. claude -p.

            timeoutSeconds = 120
            maxTokens = 4000
            # Your own naming prompt and short forms (files outside any repository); empty = proj's built-in prompt.
            shortNamesPrompt = ""
            shortForms = ""

            [[provider]]
            name = "anthropic api"
            type = "anthropic"
            url = "https://api.anthropic.com"
            model = "claude-haiku-4-5"
            apiKeyEnv = "ANTHROPIC_API_KEY"

            [[provider]]
            name = "claude cli"
            type = "command"
            command = ["claude", "-p", "--model", "haiku"]
            """;
}
