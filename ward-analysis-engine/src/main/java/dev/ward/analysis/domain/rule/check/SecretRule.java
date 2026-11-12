package dev.ward.analysis.domain.rule.check;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The data of one secret rule: what Gitleaks calls a rule, minus its id and description.
 *
 * @param regex what a secret looks like, or empty for a rule that reports a file by its path
 * @param path the paths the rule is limited to, or empty for every path
 * @param keywords lower-case words; the rule runs only on a text that holds one, or always when
 *     empty
 * @param entropy the entropy a secret must exceed, or empty
 * @param secretGroup the capture group that holds the secret, or 0 for the first group that matched
 * @param multiline whether the regex may span lines
 * @param allowlists the allowlists of the rule
 * @param message the message of every finding, which holds no source text
 */
public record SecretRule(
    Optional<Pattern> regex,
    Optional<Pattern> path,
    List<String> keywords,
    Optional<Double> entropy,
    int secretGroup,
    boolean multiline,
    List<SecretAllowlist> allowlists,
    String message) {

  /** Copies the lists and lower-cases the keywords. */
  public SecretRule {
    Objects.requireNonNull(regex, "regex");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(entropy, "entropy");
    Objects.requireNonNull(message, "message");
    keywords = keywords.stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
    allowlists = List.copyOf(allowlists);
    if (secretGroup < 0) {
      throw new IllegalArgumentException("secret-group must not be negative");
    }
    if (entropy.isPresent() && entropy.get() < 0) {
      throw new IllegalArgumentException("entropy must not be negative");
    }
    if (regex.isEmpty() && path.isEmpty()) {
      throw new IllegalArgumentException("a secret rule needs a regex or a path");
    }
  }
}
