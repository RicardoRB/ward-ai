package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The {@code secret-scan} check: SecretScanAllowlistTest. */
final class SecretScanAllowlistTest extends SecretScanSupport {

  @Test
  void aStopwordInTheSecretAllowsIt() {
    final String body = RULE + "allowlists = [{ stopwords = ['aB3d'] }]\n";

    assertThat(run(body, CONFIG, token())).isEmpty();
  }

  @Test
  void aRegexTargetingTheMatchAllowsIt() {
    final String body =
        RULE + "allowlists = [{ regexes = ['^tok_aB'], regex-target = \"match\" }]\n";

    assertThat(run(body, CONFIG, token())).isEmpty();
  }

  @Test
  void aRegexTargetingTheLineAllowsIt() {
    final String body =
        RULE + "allowlists = [{ regexes = ['example'], regex-target = \"line\" }]\n";

    assertThat(run(body, CONFIG, "# example\nk: " + token() + "\n")).hasSize(1);
    assertThat(run(body, CONFIG, "k: " + token() + " # example\n")).isEmpty();
  }

  @Test
  void conditionAndNeedsEveryCriterion() {
    final String both =
        RULE + "allowlists = [{ paths = ['\\.md$'], stopwords = ['ab3d'], condition = \"and\" }]\n";

    assertThat(run(both, "docs/a.md", token())).isEmpty();
    assertThat(run(both, CONFIG, token())).hasSize(1);
    assertThat(run(both.replace("ab3d", "zzz"), "docs/a.md", token())).hasSize(1);
  }

  @Test
  void aPathAllowlistSkipsTheFile() {
    final String body = RULE + "allowlists = [{ paths = ['^docs/'] }]\n";

    assertThat(run(body, "docs/setup.md", token())).isEmpty();
    assertThat(run(body, CONFIG, token())).hasSize(1);
  }

  @Test
  void theGlobalAllowlistSkipsALockfileAndAPlaceholder() {
    final String body = RULE + "allowlist = \"default\"\n";

    assertThat(run(body, "package-lock.json", token())).isEmpty();
    assertThat(run(body, CONFIG, token())).hasSize(1);
  }

  @Test
  void aGitleaksCommentIsNotASuppression() {
    assertThat(run(RULE, CONFIG, "k: " + token() + " # gitleaks:allow\n")).hasSize(1);
  }
}
