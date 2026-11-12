package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.RuleFileReader;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.SecretScanCheck;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The {@code secret-scan} check: SecretScanBundledRulesTest. */
final class SecretScanBundledRulesTest extends SecretScanSupport {

  /** Where the first window of a long line ends, so a token there is cut by it. */
  private static final int WINDOW_EDGE = 4090;

  @Test
  void aMultiLinePrivateKeyIsReportedAtItsHeader() {
    final String key =
        PEM_HEADER + "MIIEowIBAAKCAQEA\n".repeat(60) + "-----END RSA PRIVATE KEY-----\n";

    final List<RuleMatch> matches = run(bundled("private-key"), "keys/id.pem", "# key\n" + key);

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startLine()).isEqualTo(2);
  }

  @Test
  void aFourThousandBitPrivateKeyIsStillFound() {
    final String key =
        PEM_HEADER
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
    assertThat(line.length()).isGreaterThan(MEGABYTE);

    final List<RuleMatch> matches =
        org.junit.jupiter.api.Assertions.assertTimeout(
            Duration.ofSeconds(5), () -> run(bundled("github-pat"), "dist/app.js", line));

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startColumn())
        .isEqualTo(filler.length() + COLUMN_AFTER_PREFIX);
  }

  @Test
  void aSecretCutByTheEdgeOfAWindowIsReportedOnceAtItsStart() {
    final String decoy = "ghp_ ";
    final String token = "ghp_" + "aB3dE5gH7jK9mN1pQ3sT5vW7yZ9bD1fH3jL5";
    final int tokenAt = WINDOW_EDGE;
    final String line =
        decoy + "x".repeat(tokenAt - decoy.length()) + token + "x".repeat(WINDOW_EDGE);

    final List<RuleMatch> matches = run(bundled("github-pat"), "dist/app.js", line);

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startColumn()).isEqualTo(tokenAt + 1);
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
    assertThatThrownBy(() -> run(body, CONFIG, "a".repeat(RUN_OF_A) + "c\n"))
        .isInstanceOf(AnalysisFailedException.class)
        .hasMessage(SecretScanCheck.OVER_BUDGET);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void everySecretRuleRunsOverAMegabyteOfMixedTextInTime() {
    final String mixed =
        ("token=abcDEF123_key secret: value api" + "-key = 'x1Y2z3' password\n").repeat(16_000);
    final List<RuleDefinition> rules = RuleFileReader.read("rules/secrets/secrets.toml");
    final DeclarativeRuleMatcher matcher = new DeclarativeRuleMatcher();
    final TextFile file = new TextFile("src/mixed.txt", mixed);

    org.junit.jupiter.api.Assertions.assertTimeout(
        LIMIT, () -> rules.forEach(rule -> text(matcher.compile(rule)).textMatches(file)));
  }

  @Test
  void everySecretRuleFinishesOnAOneMegabyteSingleLineOfMixedTokenLikeText() {
    final String line =
        ("token=abcDEF123_key secret: value api" + "-key = 'x1Y2z3' password ").repeat(16_000);
    final List<RuleDefinition> rules = RuleFileReader.read("rules/secrets/secrets.toml");
    final DeclarativeRuleMatcher matcher = new DeclarativeRuleMatcher();
    final TextFile file = new TextFile("dist/bundle.js", line);

    org.junit.jupiter.api.Assertions.assertTimeout(
        LIMIT, () -> rules.forEach(rule -> text(matcher.compile(rule)).textMatches(file)));
  }
}
