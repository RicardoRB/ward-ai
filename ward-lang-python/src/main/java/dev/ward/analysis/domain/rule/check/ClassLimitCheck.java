package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a class whose size is outside a limit: more instance attributes than {@code max}, more
 * base classes than {@code max}, or, for a class without bases and decorators, fewer public methods
 * than {@code min}. Attributes are the distinct {@code self.name} targets assigned in the class's
 * own methods; bases are the names in its parenthesized list, which is the direct bases only.
 */
public final class ClassLimitCheck implements RuleCheck {

  /** The size a class is reported for. */
  public enum Problem {
    /** More distinct instance attributes than the limit. */
    INSTANCE_ATTRIBUTES,
    /** More base classes than the limit. */
    ANCESTORS,
    /** Fewer public methods than the limit, for a class with no bases and no decorator. */
    PUBLIC_METHODS
  }

  private static final Pattern SELF_TARGET = Pattern.compile("^self\\.(\\w+)[:=](?!=)");
  private static final String CLASS = "classdef";
  private static final String FUNCTION = "funcdef";
  private static final String EXPRESSION = "expr_stmt";
  private static final String PUBLIC_PREFIX = "_";

  private final Problem problem;
  private final int limit;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the size to measure
   * @param limit the limit: the largest allowed count, or the smallest for public methods
   * @param message the message of each finding
   */
  public ClassLimitCheck(final Problem problem, final int limit, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.limit = limit;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      final SyntaxNode definition = visit.node();
      if (violates(definition, visit.ancestors())) {
        found.add(new RuleMatch(definition.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean violates(final SyntaxNode definition, final List<SyntaxNode> ancestors) {
    return switch (problem) {
      case INSTANCE_ATTRIBUTES -> attributes(definition).size() > limit;
      case ANCESTORS -> bases(definition) > limit;
      case PUBLIC_METHODS ->
          !ancestors.stream().anyMatch(a -> "decorated".equals(a.kind()))
              && !ModuleDefinitions.hasBases(definition)
              && publicMethods(definition) < limit;
    };
  }

  /** The distinct names assigned as {@code self.name} in the methods of the class. */
  private static Set<String> attributes(final SyntaxNode definition) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(definition, Set.of(EXPRESSION))) {
      if (visit.ancestors().stream().skip(1).noneMatch(a -> CLASS.equals(a.kind()))) {
        final Matcher matcher = SELF_TARGET.matcher(SyntaxWalk.text(visit.node()));
        if (matcher.find()) {
          names.add(matcher.group(1));
        }
      }
    }
    return names;
  }

  /** The number of names in the class's parenthesized base list, zero when there is none. */
  private static int bases(final SyntaxNode definition) {
    final String text = SyntaxWalk.text(definition);
    final int open = text.indexOf('(');
    final int colon = text.indexOf(':');
    if (open < 0 || colon < 0 || open > colon) {
      return 0;
    }
    final String inside = text.substring(open + 1, text.lastIndexOf(')', colon));
    return inside.isEmpty() ? 0 : inside.split(",", -1).length;
  }

  /** The number of direct function definitions of the class whose names are public. */
  private static int publicMethods(final SyntaxNode definition) {
    int count = 0;
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(definition, Set.of(FUNCTION))) {
      final boolean direct =
          visit.ancestors().stream()
              .skip(1)
              .noneMatch(a -> FUNCTION.equals(a.kind()) || CLASS.equals(a.kind()));
      final String name = ModuleDefinitions.definedName(visit.node()).orElse("");
      if (direct && !name.startsWith(PUBLIC_PREFIX)) {
        count++;
      }
    }
    return count;
  }
}
