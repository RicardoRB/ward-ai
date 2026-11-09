package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a call to a function of this module whose signature the call does not satisfy, like
 * pylint {@code no-value-for-parameter}, {@code missing-kwoa}, {@code
 * kwarg-superseded-by-positional-arg} and {@code positional-only-arguments-expected}. Only
 * top-level functions are judged, and a call with a star argument is not judged.
 */
public final class SameModuleCallCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** A required positional parameter the call does not supply. */
    NO_VALUE_FOR_PARAMETER,
    /** A required keyword-only parameter the call does not supply. */
    MISSING_KWOA,
    /** A keyword argument that names a positional parameter the call already fills. */
    KWARG_SUPERSEDED,
    /** A keyword argument that names a positional-only parameter. */
    POSITIONAL_ONLY
  }

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern CALL_HEAD = Pattern.compile("^(\\w+)\\(");
  private static final Pattern KEYWORD = Pattern.compile("^(\\w+)=(?!=)(.*)$", Pattern.DOTALL);
  private static final char OPEN = '(';
  private static final char CLOSE = ')';

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public SameModuleCallCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, FunctionSignatures.Signature> functions =
        FunctionSignatures.topLevelFunctions(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final String text = SyntaxWalk.text(visit.node());
      final Matcher head = CALL_HEAD.matcher(text);
      if (head.find() && closes(text, head.end() - 1)) {
        final FunctionSignatures.Signature signature = functions.get(head.group(1));
        final String inside = text.substring(head.end(), text.length() - 1);
        if (signature != null && violates(signature, ArgumentList.split(inside))) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** True when the bracket at {@code open} closes at the last character of the text. */
  private static boolean closes(final String text, final int open) {
    int depth = 0;
    for (int index = open; index < text.length(); index++) {
      final char c = text.charAt(index);
      if (c == OPEN) {
        depth++;
      } else if (c == CLOSE) {
        depth--;
        if (depth == 0) {
          return index == text.length() - 1;
        }
      }
    }
    return false;
  }

  private boolean violates(
      final FunctionSignatures.Signature signature, final List<String> arguments) {
    final List<String> keywords = new ArrayList<>();
    int positional = 0;
    for (final String argument : arguments) {
      if (argument.startsWith("*")) {
        return false;
      }
      final Matcher named = KEYWORD.matcher(argument);
      if (named.matches()) {
        keywords.add(named.group(1));
      } else {
        positional++;
      }
    }
    return switch (problem) {
      case NO_VALUE_FOR_PARAMETER -> missingPositional(signature, positional, keywords);
      case MISSING_KWOA -> missingKeywordOnly(signature, keywords);
      case KWARG_SUPERSEDED -> superseded(signature, positional, keywords);
      case POSITIONAL_ONLY -> positionalOnlyByKeyword(signature, keywords);
    };
  }

  private static boolean missingPositional(
      final FunctionSignatures.Signature signature,
      final int positional,
      final List<String> keywords) {
    for (int index = positional; index < signature.positional().size(); index++) {
      final FunctionSignatures.Param param = signature.positional().get(index);
      if (!param.hasDefault() && !keywords.contains(param.name())) {
        return true;
      }
    }
    return false;
  }

  private static boolean missingKeywordOnly(
      final FunctionSignatures.Signature signature, final List<String> keywords) {
    return signature.keywordOnly().stream()
        .anyMatch(param -> !param.hasDefault() && !keywords.contains(param.name()));
  }

  private static boolean superseded(
      final FunctionSignatures.Signature signature,
      final int positional,
      final List<String> keywords) {
    for (int index = 0; index < Math.min(positional, signature.positional().size()); index++) {
      if (keywords.contains(signature.positional().get(index).name())) {
        return true;
      }
    }
    return false;
  }

  private static boolean positionalOnlyByKeyword(
      final FunctionSignatures.Signature signature, final List<String> keywords) {
    return !signature.hasKwargs()
        && signature.positionalOnly().stream().anyMatch(keywords::contains);
  }
}
