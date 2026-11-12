package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The rule data and runners that the secret-scan tests share. */
class SecretScanCheckSupport {

  static final String TOKEN = "tok_" + "aB3dE5gH7jK9mN1pQ3sT";
  static final int TOKEN_COLUMN = 8;

  Pattern regex(final String text) {
    return Pattern.compile(text, Pattern.UNIX_LINES);
  }

  SecretRule rule(final Optional<Double> entropy, final List<String> keywords) {
    return new SecretRule(
        Optional.of(regex("tok_[0-9a-zA-Z]{20}")),
        Optional.empty(),
        keywords,
        entropy,
        0,
        false,
        List.of(),
        "m");
  }

  List<RuleMatch> run(final SecretRule rule, final String path, final String text) {
    return new SecretScanCheck(rule, List.of()).textMatches(new TextFile(path, text));
  }

  SecretAllowlist allowlist(
      final List<String> paths,
      final List<String> regexes,
      final List<String> stopwords,
      final SecretAllowlist.Target target,
      final boolean all) {
    return new SecretAllowlist(
        paths.stream().map(this::regex).toList(),
        regexes.stream().map(this::regex).toList(),
        stopwords,
        target,
        all);
  }

  SecretAllowlist.Target target(final int index) {
    return SecretAllowlist.Target.values()[index];
  }

  SecretRule withList(final SecretRule base, final SecretAllowlist list) {
    return new SecretRule(
        base.regex(),
        base.path(),
        base.keywords(),
        base.entropy(),
        base.secretGroup(),
        base.multiline(),
        List.of(list),
        base.message());
  }

  /** Whether the list allows the token on the second line of a file with a comment first. */
  boolean allowed(final SecretRule base, final SecretAllowlist list) {
    return run(withList(base, list), "a.yml", "# c\n" + TOKEN + "\n").isEmpty();
  }
}
