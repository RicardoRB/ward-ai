package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The secret-scan evaluation order, built from rule data without any TOML. */
class SecretScanCheckTest extends SecretScanCheckSupport {

  private static final double THRESHOLD = 3.0;
  private static final double IMPOSSIBLE = 9.0;
  private static final int BODY = 20;
  private static final int RUN = 60;

  @Test
  void reportsTheMatchStartAndOnlyWhenAKeywordIsInTheText() {
    final SecretRule withKeyword = rule(Optional.empty(), List.of("TOK_"));

    final List<RuleMatch> matches = run(withKeyword, "a.yml", "a: 1\ntoken: " + TOKEN + "\n");

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startLine()).isEqualTo(2);
    assertThat(matches.get(0).span().startColumn()).isEqualTo(TOKEN_COLUMN);
    assertThat(run(rule(Optional.empty(), List.of("absent")), "a.yml", TOKEN)).isEmpty();
  }

  @Test
  void entropyMustBeAboveTheThresholdAndZeroDisablesIt() {
    final String flat = "tok_" + "a".repeat(BODY);

    assertThat(run(rule(Optional.of(THRESHOLD), List.of()), "a", TOKEN)).hasSize(1);
    assertThat(run(rule(Optional.of(THRESHOLD), List.of()), "a", flat)).isEmpty();
    assertThat(run(rule(Optional.of(0.0), List.of()), "a", flat)).hasSize(1);
    assertThat(run(rule(Optional.of(IMPOSSIBLE), List.of()), "a", TOKEN)).isEmpty();
  }

  @Test
  void theSecretGroupAndTheFirstGroupThatMatchedDecideTheSecret() {
    final SecretRule group =
        new SecretRule(
            Optional.of(regex("(user)=(tok_[0-9a-zA-Z]{20})")),
            Optional.empty(),
            List.of(),
            Optional.of(THRESHOLD),
            2,
            false,
            List.of(),
            "m");
    final SecretRule first =
        new SecretRule(
            Optional.of(regex("(x)?(tok_[0-9a-zA-Z]{20})")),
            Optional.empty(),
            List.of(),
            Optional.of(THRESHOLD),
            0,
            false,
            List.of(),
            "m");

    assertThat(run(group, "a", "user=" + TOKEN)).hasSize(1);
    assertThat(run(group, "a", "user=tok_" + "a".repeat(BODY))).isEmpty();
    assertThat(run(first, "a", TOKEN)).hasSize(1);
  }

  @Test
  void aPathOnlyRuleReportsTheFileWithNoPosition() {
    final SecretRule pathOnly =
        new SecretRule(
            Optional.empty(),
            Optional.of(regex("\\.p12$")),
            List.of(),
            Optional.empty(),
            0,
            false,
            List.of(),
            "m");

    final List<RuleMatch> matches = run(pathOnly, "certs/a.p12", "binary");

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span()).isNull();
    assertThat(run(pathOnly, "certs/a.txt", "x")).isEmpty();
  }

  @Test
  void aPathFilterLimitsARegexRuleToMatchingPaths() {
    final SecretRule onPath =
        new SecretRule(
            Optional.of(regex("tok_[0-9a-zA-Z]{20}")),
            Optional.of(regex("\\.yml$")),
            List.of(),
            Optional.empty(),
            0,
            false,
            List.of(),
            "m");

    assertThat(run(onPath, "a.yml", TOKEN)).hasSize(1);
    assertThat(run(onPath, "a.txt", TOKEN)).isEmpty();
  }

  @Test
  void stopwordsRegexesAndTargetsAllowAFinding() {
    final SecretRule base = rule(Optional.empty(), List.of());

    assertThat(allowed(base, allowlist(List.of(), List.of(), List.of("ab3d"), target(0), false)))
        .isTrue();
    assertThat(allowed(base, allowlist(List.of(), List.of("^tok_aB"), List.of(), target(1), false)))
        .isTrue();
    assertThat(allowed(base, allowlist(List.of(), List.of("^tok_"), List.of(), target(2), false)))
        .isTrue();
    assertThat(allowed(base, allowlist(List.of(), List.of("^zzz"), List.of(), target(0), false)))
        .isFalse();
  }

  @Test
  void conditionAndNeedsEveryCriterionThatIsPresent() {
    final SecretRule base = rule(Optional.empty(), List.of());
    final SecretAllowlist both =
        allowlist(List.of("\\.md$"), List.of(), List.of("ab3d"), target(0), true);
    final SecretAllowlist wrongWord =
        allowlist(List.of("\\.md$"), List.of(), List.of("zzz"), target(0), true);
    final SecretAllowlist none = allowlist(List.of(), List.of(), List.of(), target(0), true);

    assertThat(run(withList(base, both), "docs/a.md", TOKEN)).isEmpty();
    assertThat(run(withList(base, both), "src/a.yml", TOKEN)).hasSize(1);
    assertThat(run(withList(base, wrongWord), "docs/a.md", TOKEN)).hasSize(1);
    assertThat(run(withList(base, none), "docs/a.md", TOKEN)).hasSize(1);
  }

  @Test
  void aPathAllowlistAndTheGlobalListSkipTheFile() {
    final SecretRule base = rule(Optional.empty(), List.of());
    final SecretAllowlist docs =
        allowlist(List.of("^docs/"), List.of(), List.of(), target(0), false);

    assertThat(run(withList(base, docs), "docs/a.md", TOKEN)).isEmpty();
    assertThat(new SecretScanCheck(base, List.of(docs)).textMatches(new TextFile("docs/a", TOKEN)))
        .isEmpty();
    assertThat(new SecretScanCheck(base, List.of(docs)).textMatches(new TextFile("src/a", TOKEN)))
        .hasSize(1);
  }

  @Test
  void aRuleOverItsWorkBudgetIsAnErrorWithAFixedReason() {
    final SecretRule evil =
        new SecretRule(
            Optional.of(regex("(.*a){12}x")),
            Optional.empty(),
            List.of("a"),
            Optional.empty(),
            0,
            false,
            List.of(),
            "m");

    assertThatThrownBy(() -> run(evil, "a", "a".repeat(RUN)))
        .isInstanceOf(AnalysisFailedException.class)
        .hasMessage(SecretScanCheck.OVER_BUDGET);
  }

  @Test
  void anUnusableRuleIsRejected() {
    final Optional<Pattern> none = Optional.empty();
    final List<SecretAllowlist> lists = List.of();

    assertThatThrownBy(
            () -> new SecretRule(none, none, List.of(), Optional.empty(), 0, false, lists, "m"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new SecretRule(
                    Optional.of(regex("a")),
                    none,
                    List.of(),
                    Optional.empty(),
                    -1,
                    false,
                    lists,
                    "m"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new SecretRule(
                    Optional.of(regex("a")),
                    none,
                    List.of(),
                    Optional.of(-1.0),
                    0,
                    false,
                    lists,
                    "m"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
