package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.RuleFileReader;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.TextCompiledRule;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** The rule bodies, bundled rules and runners that the secret-scan tests share. */
class SecretScanSupport {

  static final String RULE =
      """
      check = "secret-scan"
      message = "Potential secret."
      regex = 'tok_[0-9a-zA-Z]{20}'
      keywords = ['tok_']
      entropy = 3.0
      """;
  static final String CONFIG = "config/app.yml";

  static final int TOKEN_BODY = 20;
  static final int TOKEN_LINE_NUMBER = 3;
  static final int TOKEN_COLUMN = 8;
  static final int PREFIX_LENGTH = 8;
  static final int MEGABYTE = 1_000_000;
  static final int COLUMN_AFTER_PREFIX = 10;
  static final int RUN_OF_A = 60;
  static final Duration LIMIT = Duration.ofSeconds(30);
  // Built from two literals, so that no source file holds a private key header.
  static final String PEM_HEADER = "-----BEGIN " + "RSA PRIVATE KEY-----\n";

  /** A fake token built at run time, so no source file holds one. */
  String token() {
    return "tok_" + "aB3dE5gH7jK9mN1pQ3sT".substring(0, TOKEN_BODY);
  }

  static void definitionCompiled(final String body) {
    new DeclarativeRuleMatcher().compile(definition(body));
  }

  static TextCompiledRule text(final CompiledRule rule) {
    return (TextCompiledRule) rule;
  }

  static RuleDefinition bundled(final String name) {
    return RuleFileReader.read("rules/secrets/secrets.toml").stream()
        .filter(rule -> rule.id().equals("ward:secrets/" + name))
        .findFirst()
        .orElseThrow();
  }

  static RuleDefinition definition(final String body) {
    return new RuleDefinition(
        "ward:secrets/test", Set.of(), RuleCapability.TEXT, AnalysisSeverity.ERROR, body);
  }

  static List<RuleMatch> run(final String body, final String path, final String text) {
    return run(definition(body), path, text);
  }

  static List<RuleMatch> run(final RuleDefinition rule, final String path, final String text) {
    return text(new DeclarativeRuleMatcher().compile(rule)).textMatches(new TextFile(path, text));
  }
}
