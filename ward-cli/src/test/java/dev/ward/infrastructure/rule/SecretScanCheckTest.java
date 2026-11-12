package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.RuleFileReader;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptionType;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.domain.rule.check.SecretScanCheck;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@code secret-scan} check, in the evaluation order of Gitleaks 8.30.1. */
final class SecretScanCheckTest {

  private static final String RULE =
      """
      check = "secret-scan"
      message = "Potential secret."
      regex = 'tok_[0-9a-zA-Z]{20}'
      keywords = ['tok_']
      entropy = 3.0
      """;
  private static final String CONFIG = "config/app.yml";

  /** A fake token built at run time, so no source file holds one. */
  private static String token() {
    return "tok_" + "aB3dE5gH7jK9mN1pQ3sT".substring(0, 20);
  }

  @Test
  void reportsATokenAtTheLineAndColumnWhereTheMatchStarts() {
    final List<RuleMatch> matches = run(RULE, CONFIG, "a: 1\nb:\n  key: " + token() + "\n");

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startLine()).isEqualTo(3);
    assertThat(matches.get(0).span().startColumn()).isEqualTo(8);
  }

  @Test
  void aFindingHoldsNeitherTheSecretNorItsPrefix() {
    final String text = "key: " + token() + "\n";

    final String finding = run(RULE, CONFIG, text).toString();

    assertThat(finding).doesNotContain(token()).doesNotContain(token().substring(0, 8));
    assertThat(finding).doesNotContain("key:");
  }

  @Test
  void aMatchWithoutAKeywordInTheTextIsNotReported() {
    final String body = RULE.replace("keywords = ['tok_']", "keywords = ['nothere']");

    assertThat(run(body, CONFIG, token())).isEmpty();
  }

  @Test
  void keywordsAreComparedWithoutCase() {
    final String body = RULE.replace("keywords = ['tok_']", "keywords = ['TOK_']");

    assertThat(run(body, CONFIG, token())).hasSize(1);
  }

  @Test
  void aSecretAtOrBelowTheEntropyThresholdIsNotReported() {
    final String flat = "tok_" + "a".repeat(20);

    assertThat(run(RULE, CONFIG, flat)).isEmpty();
  }

  @Test
  void theEntropyOptionReplacesTheThreshold() {
    final RuleDefinition strict =
        RuleOptions.apply(
            new RuleDefinition(
                "ward:secrets/test",
                Set.of(),
                RuleCapability.TEXT,
                AnalysisSeverity.ERROR,
                RULE,
                "secrets",
                Set.of(),
                List.of(new RuleOption("entropy", RuleOptionType.NUMBER)),
                0),
            Map.of("entropy", 4.6));

    assertThat(run(strict, CONFIG, token())).isEmpty();
    assertThat(run(RULE, CONFIG, token())).hasSize(1);
  }

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

  @Test
  void theSecretGroupDecidesWhatEntropyAndAllowlistsSee() {
    final String body =
        """
        check = "secret-scan"
        message = "m"
        regex = '(user)=(tok_[0-9a-zA-Z]{20})'
        keywords = ['tok_']
        secret-group = 2
        entropy = 3.0
        """;

    assertThat(run(body, CONFIG, "user=" + token())).hasSize(1);
    assertThat(run(body, CONFIG, "user=tok_" + "a".repeat(20))).isEmpty();
  }

  @Test
  void aPathOnlyRuleReportsTheFileWithNoLine() {
    final String body =
        """
        check = "secret-scan"
        message = "m"
        path = '\\.p12$'
        """;

    final List<RuleMatch> matches = run(body, "certs/store.p12", "binary");

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span()).isNull();
    assertThat(run(body, "certs/store.txt", "x")).isEmpty();
  }

  @Test
  void aMultiLinePrivateKeyIsReportedAtItsHeader() {
    final String key =
        "-----BEGIN RSA PRIVATE KEY-----\n"
            + ("MIIEowIBAAKCAQEA\n".repeat(60))
            + "-----END RSA PRIVATE KEY-----\n";

    final List<RuleMatch> matches = run(bundled("private-key"), "keys/id.pem", "# key\n" + key);

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startLine()).isEqualTo(2);
  }

  @Test
  void aFourThousandBitPrivateKeyIsStillFound() {
    final String key =
        "-----BEGIN RSA PRIVATE KEY-----\n"
            + "MIIJKQIBAAKCAgEAq7Zx3Lp9Wv2Nc8Rt4Ym6Bh1Dk5Fs0Gj+/Qe7Uo2Ia9Xw3Pb8Vn4Hc6Tr5Ml\n"
                .repeat(60)
            + "-----END RSA PRIVATE KEY-----\n";

    assertThat(run(bundled("private-key"), "keys/id.pem", key)).hasSize(1);
  }

  @Test
  void aTokenInAOneMegabyteLineIsFoundQuickly() {
    final String filler = "var a=function(){return 1};".repeat(20_000);
    final String line =
        filler
            + "const t='ghp_"
            + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5".substring(0, 36)
            + "';"
            + filler;
    assertThat(line.length()).isGreaterThan(1_000_000);

    final List<RuleMatch> matches =
        org.junit.jupiter.api.Assertions.assertTimeout(
            Duration.ofSeconds(5), () -> run(bundled("github-pat"), "dist/app.js", line));

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startColumn()).isEqualTo(filler.length() + 10);
  }

  @Test
  void aRuleThatGoesOverItsWorkBudgetIsAnErrorNeverAPass() {
    final String body =
        """
        check = "secret-scan"
        message = "m"
        regex = '(.*a){12}x'
        keywords = ['a']
        """;

    final long start = System.nanoTime();
    assertThatThrownBy(() -> run(body, CONFIG, "a".repeat(60) + "c\n"))
        .isInstanceOf(AnalysisFailedException.class)
        .hasMessage(SecretScanCheck.OVER_BUDGET);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void everySecretRuleRunsOverAMegabyteOfMixedTextInTime() {
    final String mixed =
        "token=abcDEF123_key secret: value api-key = 'x1Y2z3' password\n".repeat(16_000);
    final List<RuleDefinition> rules = RuleFileReader.read("rules/secrets/secrets.toml");
    final DeclarativeRuleMatcher matcher = new DeclarativeRuleMatcher();
    final TextFile file = new TextFile("src/mixed.txt", mixed);

    org.junit.jupiter.api.Assertions.assertTimeout(
        Duration.ofSeconds(30),
        () -> rules.forEach(rule -> matcher.compile(rule).textMatches(file)));
  }

  @Test
  void everyUnusableKeyOrValueIsRejectedAtCompileTime() {
    final List<String> bodies =
        List.of(
            "regex = '(unclosed'",
            "regex = 'a'\nentropy = -1.0",
            "regex = 'a'\nentropy = \"high\"",
            "regex = 'a'\nunknown-key = 1",
            "regex = 'a'\nsecret-group = 3",
            "regex = 'a'\nsecret-group = -1",
            "regex = 'a'\nspan = \"page\"",
            "path = '(unclosed'",
            "regex = 'a'\nallowlist = \"other\"",
            "regex = 'a'\nallowlists = [{ colour = \"red\" }]",
            "regex = 'a'\nallowlists = [{ regexes = ['(x'] }]",
            "regex = 'a'\nallowlists = [{ regex-target = \"file\" }]",
            "regex = 'a'\nallowlists = [{ condition = \"xor\" }]",
            "regex = 'a'\nkeywords = [1]",
            "keywords = ['a']");
    for (final String body : bodies) {
      assertThatThrownBy(
              () -> definitionCompiled("check = \"secret-scan\"\nmessage = \"m\"\n" + body))
          .as(body)
          .isInstanceOf(AnalysisFailedException.class);
    }
  }

  private static void definitionCompiled(final String body) {
    new DeclarativeRuleMatcher().compile(definition(body));
  }

  private static RuleDefinition bundled(final String name) {
    return RuleFileReader.read("rules/secrets/secrets.toml").stream()
        .filter(rule -> rule.id().equals("ward:secrets/" + name))
        .findFirst()
        .orElseThrow();
  }

  private static RuleDefinition definition(final String body) {
    return new RuleDefinition(
        "ward:secrets/test", Set.of(), RuleCapability.TEXT, AnalysisSeverity.ERROR, body);
  }

  private static List<RuleMatch> run(final String body, final String path, final String text) {
    return run(definition(body), path, text);
  }

  private static List<RuleMatch> run(
      final RuleDefinition rule, final String path, final String text) {
    return new DeclarativeRuleMatcher().compile(rule).textMatches(new TextFile(path, text));
  }
}
