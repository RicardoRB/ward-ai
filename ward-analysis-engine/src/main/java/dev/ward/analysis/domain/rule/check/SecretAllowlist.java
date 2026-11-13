package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One allowlist of the Gitleaks configuration, as data: paths, regexes and stopwords, what the
 * regexes read, and whether one criterion or all of them allow a finding.
 *
 * @param paths patterns of file paths, searched anywhere in the path
 * @param regexes patterns searched in the {@code target}
 * @param stopwords lower-case words; one inside the lower-cased secret allows the finding
 * @param target what the regexes read
 * @param all whether every criterion that is present must hold, instead of any
 */
public record SecretAllowlist(
    List<Pattern> paths,
    List<Pattern> regexes,
    List<String> stopwords,
    Target target,
    boolean all) {

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

  /** Copies the lists. */
  public SecretAllowlist {
    paths = List.copyOf(paths);
    regexes = List.copyOf(regexes);
    stopwords = List.copyOf(stopwords);
    Objects.requireNonNull(target, "target");
  }

  /** Returns whether this allowlist allows a file by its path alone, so its rule skips the file. */
  boolean skipsFile(final String path) {
    return pathMatches(path) && (!all || (regexes.isEmpty() && stopwords.isEmpty()));
  }

  boolean pathMatches(final String path) {
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
