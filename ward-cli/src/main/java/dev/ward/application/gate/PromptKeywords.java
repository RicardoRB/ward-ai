package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leaves out the secret rules that cannot match a prompt, before they are built. A secret rule runs
 * on a text only when the text holds one of its {@code keywords}, so a rule whose keywords none of
 * the texts holds would find nothing; building it is the main cost of a prompt scan. A rule whose
 * keywords cannot be read with certainty, or that has none, is kept.
 */
final class PromptKeywords {
  private static final char LIST_END = ']';
  private static final Pattern KEY = Pattern.compile("(?m)^keywords\\s*=\\s*\\[");

  private PromptKeywords() {}

  /** Returns the rules among {@code rules} that may match one of {@code texts}. */
  static List<RuleDefinition> relevant(
      final List<RuleDefinition> rules,
      final List<TextFile> texts
  ) {
    return rules.stream()
        .filter(rule -> keywords(rule.body()).map(words -> holdsAny(texts, words)).orElse(true))
        .toList();
  }

  private static boolean holdsAny(final List<TextFile> texts, final List<String> words) {
    return texts.stream().anyMatch(text -> words.stream().anyMatch(text.lower()::contains));
  }

  /** Returns the keywords of a rule body, or empty when there are none or they are unreadable. */
  static Optional<List<String>> keywords(final String body) {
    final Matcher key = KEY.matcher(body);
    if (!key.find()) {
      return Optional.empty();
    }
    final List<String> words = new ArrayList<>();
    int index = key.end();
    while (index < body.length()) {
      final char current = body.charAt(index);
      if (current == LIST_END) {
        return words.isEmpty() ? Optional.empty() : Optional.of(words);
      }
      if (current == '\'' || current == '"') {
        final int end = closing(body, index);
        if (end < 0) {
          return Optional.empty();
        }
        words.add(body.substring(index + 1, end));
        index = end + 1;
      } else if (Character.isWhitespace(current) || current == ',') {
        index++;
      } else {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /** Returns the index of the quote that closes the string at {@code open}; -1 when it does not. */
  private static int closing(final String body, final int open) {
    final char quote = body.charAt(open);
    for (int index = open + 1; index < body.length(); index++) {
      final char current = body.charAt(index);
      if (quote == '"' && current == '\\') {
        return -1;
      }
      if (current == quote) {
        return index;
      }
    }
    return -1;
  }
}
