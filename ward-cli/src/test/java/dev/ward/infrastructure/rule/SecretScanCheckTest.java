package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RuleOptionType;
import dev.ward.analysis.domain.rule.RuleOptions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@code secret-scan} check: SecretScanCheckTest. */
final class SecretScanCheckTest extends SecretScanSupport {

  @Test
  void reportsATokenAtTheLineAndColumnWhereTheMatchStarts() {
    final List<RuleMatch> matches = run(RULE, CONFIG, "a: 1\nb:\n  key: " + token() + "\n");

    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).span().startLine()).isEqualTo(TOKEN_LINE_NUMBER);
    assertThat(matches.get(0).span().startColumn()).isEqualTo(TOKEN_COLUMN);
  }

  @Test
  void aFindingHoldsNeitherTheSecretNorItsPrefix() {
    final String text = "key: " + token() + "\n";

    final String finding = run(RULE, CONFIG, text).toString();

    assertThat(finding).doesNotContain(token()).doesNotContain(token().substring(0, PREFIX_LENGTH));
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
    final String flat = "tok_" + "a".repeat(TOKEN_BODY);

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
    assertThat(run(body, CONFIG, "user=tok_" + "a".repeat(TOKEN_BODY))).isEmpty();
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
}
