package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * One allowlist of the Gitleaks configuration, as data: paths, regexes and stopwords, what the
 * regexes read, and whether one criterion or all of them allow a finding.
 *
 * <p>Whether a path matches depends only on the allowlist and the path, and every rule that shares
 * the allowlist asks it for every file, so the answer is kept per path.
 */
public final class SecretAllowlist {
  private final List<Pattern> paths;
  private final List<Pattern> regexes;
  private final List<String> stopwords;
  private final Target target;
  private final boolean all;
  private final Map<String, Boolean> pathAnswers = new ConcurrentHashMap<>();

  /** What an allowlist regex is searched in. */
  public enum Target {
    /** The secret. */
    SECRET,
    /** The whole match of the rule. */
    MATCH,
    /** The line of the match. */
    LINE
  }

  /** What a rule matched, as an allowlist sees it. */
  record Candidate(String path, String secret, String match, String line) {}

  /**
   * Creates the allowlist and copies the lists.
   *
   * @param paths patterns of file paths, searched anywhere in the path
   * @param regexes patterns searched in the {@code target}
   * @param stopwords lower-case words; one inside the lower-cased secret allows the finding
   * @param target what the regexes read
   * @param all whether every criterion that is present must hold, instead of any
   */
  public SecretAllowlist(
      final List<Pattern> paths,
      final List<Pattern> regexes,
      final List<String> stopwords,
      final Target target,
      final boolean all
  ) {
    this.paths = List.copyOf(paths);
    this.regexes = List.copyOf(regexes);
    this.stopwords = List.copyOf(stopwords);
    this.target = Objects.requireNonNull(target, "target");
    this.all = all;
  }

  /** Returns whether this allowlist allows a file by its path alone, so its rule skips the file. */
  boolean skipsFile(final String path) {
    return pathMatches(path) && (!all || (regexes.isEmpty() && stopwords.isEmpty()));
  }

  boolean pathMatches(final String path) {
    return !paths.isEmpty() && pathAnswers.computeIfAbsent(path, this::searchPaths);
  }

  private boolean searchPaths(final String path) {
    return paths.stream().anyMatch(pattern -> pattern.matcher(path).find());
  }

  /** Returns whether this allowlist allows {@code candidate}. */
  boolean allows(final Candidate candidate, final WorkBudget budget) {
    final List<Boolean> held = new ArrayList<>();
    if (!paths.isEmpty()) {
      held.add(pathMatches(candidate.path()));
    }
    if (!regexes.isEmpty()) {
      held.add(regexMatches(candidate, budget));
    }
    if (!stopwords.isEmpty()) {
      held.add(hasStopword(candidate.secret()));
    }
    if (held.isEmpty()) {
      return false;
    }
    return all ? !held.contains(false) : held.contains(true);
  }

  private boolean regexMatches(final Candidate candidate, final WorkBudget budget) {
    final String subject =
        switch (target) {
          case MATCH -> candidate.match();
          case LINE -> candidate.line();
          case SECRET -> candidate.secret();
          default -> candidate.secret();
        };
    final CharSequence text = new BudgetedText(subject, budget);
    return regexes.stream().anyMatch(pattern -> pattern.matcher(text).find());
  }

  private boolean hasStopword(final String secret) {
    final String lower = secret.toLowerCase(Locale.ROOT);
    return stopwords.stream().anyMatch(lower::contains);
  }
}
