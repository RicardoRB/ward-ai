package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.secrets.PromptFinding;
import dev.ward.domain.secrets.PromptInput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The prompt scan of the {@code secrets} check, with the fake secrets of the rule fixtures. */
class SecretScanPromptTest extends SecretGateTestSupport {
  private static final String RULE = "ward:secrets/github-pat";
  private static final int FIRST_COLUMN = 1;

  private PromptScan scan(final SecretsOptions options) {
    return new SecretGates(ports(), (projectRoot, since, sink) -> sink.accept(List.of()))
        .promptScan(secretsCheck(options));
  }

  private static String fixture(final String rule, final String name) throws IOException {
    try (InputStream stream =
        SecretScanPromptTest.class.getResourceAsStream("/rules/secrets/" + rule + "/" + name)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
    }
  }

  private SecretsOptions prompts(final List<String> excludePaths) {
    return new SecretsOptions(false, Optional.empty(), excludePaths, true);
  }

  @Test
  @DisplayName("a prompt with a secret")
  void aPromptWithASecret() throws IOException {
    // Given an agent with Ward's prompt hook installed, and a fake secret from the fixtures
    final String secret = fixture("github-pat", "violating.txt");
    final PromptScan scan = scan(prompts(List.of()));

    // When I submit a prompt that contains it
    final List<PromptFinding> found =
        scan.scan(root, new PromptInput("please use\nthis token: " + secret + " now"));

    // Then the finding names the rule, the line and the column, and holds no secret
    assertThat(found)
        .contains(new PromptFinding(null, RULE, 2, "this token: ".length() + FIRST_COLUMN));
    assertThat(found.toString()).doesNotContain(secret);
  }

  @Test
  @DisplayName("a clean prompt")
  void aCleanPrompt() {
    // Given the same configuration
    final PromptScan scan = scan(prompts(List.of()));

    // When I submit a prompt without secrets
    final List<PromptFinding> found = scan.scan(root, new PromptInput("Rename the class Foo."));

    // Then nothing is found, so the prompt is sent unchanged
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("a file attached to the prompt")
  void aFileAttachedToThePrompt() throws IOException {
    // Given a prompt that attaches files holding a fake secret, one under exclude-paths
    final String secret = fixture("github-pat", "violating.txt");
    write("config/app.yml", "a: 1\ntoken: " + secret + "\n");
    write("fixtures/app.yml", "a: 1\ntoken: " + secret + "\n");
    final PromptInput prompt =
        new PromptInput("Look at @config/app.yml", List.of("fixtures/app.yml"));

    // When I submit it
    final List<PromptFinding> found = scan(prompts(List.of("fixtures/**"))).scan(root, prompt);

    // Then the file, the rule, the line and the column are named, and the excluded file is not
    assertThat(found).contains(
        new PromptFinding("config/app.yml", RULE, 2, "token: ".length() + 1)
    );
    assertThat(found).noneMatch(finding -> "fixtures/app.yml".equals(finding.file()));
  }

  @Test
  @DisplayName("a rule with a path filter")
  void aRuleWithAPathFilter() throws IOException {
    // Given a message that only a rule with a path filter matches
    final String text = fixture("freemius-secret-key", "violating.php");
    write("settings.php", text + "\n");
    final PromptScan scan = scan(prompts(List.of()));

    // When I submit it as a message
    final List<PromptFinding> inMessage = scan.scan(root, new PromptInput(text));

    // Then that rule does not run on the message, though it matches the same text in a .php file
    assertThat(inMessage).noneMatch(finding -> finding.ruleId().contains("freemius"));
    assertThat(scan.scan(root, new PromptInput("see @settings.php")))
        .anyMatch(finding -> finding.ruleId().contains("freemius"));
  }

  @Test
  @DisplayName("an allowlisted test value")
  void anAllowlistedTestValue() {
    // Given a prompt that contains a value the secrets rules allowlist
    final String allowed = "ghp_" + "abcdefghijklmnopqrstuvwxyz" + "0123456789";

    // When I submit it
    final List<PromptFinding> found =
        scan(prompts(List.of())).scan(root, new PromptInput("token " + allowed));

    // Then it is sent
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("a ward-rules.toml setting for a secret rule is honoured on the prompt path")
  void aRulesFileSettingForASecretRuleIsHonoured() throws IOException {
    // Given a ward-rules.toml that tunes a Java rule and one secret rule, and a secret in two files
    final String secret = fixture("github-pat", "violating.txt");
    write(
        "ward-rules.toml",
        "version = 1\n[select]\npreset = \"none\"\nlanguages = [\"java\"]\n"
            + "include = [\"java.method-length\"]\n[rules.java.method-length]\n"
            + "[rules.secrets.github-pat]\nexclude-paths = [\"docs/**\"]\n"
    );
    write("docs/a.txt", "token: " + secret + "\n");
    write("src/b.txt", "token: " + secret + "\n");

    // When I submit a prompt that attaches both
    final List<PromptFinding> found =
        scan(prompts(List.of())).scan(root, new PromptInput("see @docs/a.txt and @src/b.txt"));

    // Then the rule skips docs/** as the file says, still finds the secret elsewhere, and the Java
    // entries of the file neither break nor slow the prompt path
    assertThat(found).contains(new PromptFinding("src/b.txt", RULE, 1, "token: ".length() + 1));
    assertThat(found)
        .noneMatch(finding -> RULE.equals(finding.ruleId()) && "docs/a.txt".equals(finding.file()));
  }

  @Test
  @DisplayName("a ward-rules.toml exclusion of a secret rule is honoured on the prompt path")
  void aRulesFileExclusionOfASecretRuleIsHonoured() throws IOException {
    // Given a ward-rules.toml that excludes one secret rule
    final String secret = fixture("github-pat", "violating.txt");
    write("ward-rules.toml", "version = 1\n[select]\nexclude = [\"secrets.github-pat\"]\n");

    // When I submit a prompt with the secret
    final List<PromptFinding> found = scan(prompts(List.of())).scan(root, new PromptInput(secret));

    // Then that rule does not report it
    assertThat(found).noneMatch(finding -> RULE.equals(finding.ruleId()));
  }
}
