package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a class pattern in a {@code match} statement that has more positional sub-patterns than
 * the class takes. Builtin classes take one; a class defined once in the same module takes the
 * length of its literal {@code __match_args__}. Other classes are not checked.
 */
public final class PatternCountCheck implements RuleCheck {

  private static final Set<String> BUILTINS =
      Set.of("bool", "bytearray", "bytes", "dict", "float", "frozenset", "int", "list", "set", "str", "tuple");
  private static final Pattern CLASS_PATTERN = Pattern.compile("^([A-Za-z_]\\w*)\\((.*)\\)$");
  private static final Pattern MATCH_ARGS = Pattern.compile("^__match_args__=[\\(\\[](.*)[\\)\\]]$");
  private static final int BUILTIN_POSITIONAL = 1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public PatternCountCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("class_pattern"))) {
      final Matcher pattern = CLASS_PATTERN.matcher(SyntaxWalk.text(visit.node()).strip());
      if (pattern.matches()) {
        final int positional = positionalCount(pattern.group(2));
        final Optional<Integer> allowed = allowed(pattern.group(1), definitions);
        if (allowed.isPresent() && positional > allowed.get()) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** The positional sub-patterns allowed by a class, when they are known. */
  private static Optional<Integer> allowed(final String name, final Map<String, SyntaxNode> definitions) {
    if (BUILTINS.contains(name) && !definitions.containsKey(name)) {
      return Optional.of(BUILTIN_POSITIONAL);
    }
    final SyntaxNode definition = definitions.get(name);
    if (definition == null || !"classdef".equals(definition.kind())) {
      return Optional.empty();
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(definition, Set.of("expr_stmt"))) {
      final Matcher args = MATCH_ARGS.matcher(SyntaxWalk.text(visit.node()).strip());
      if (args.matches()) {
        return Optional.of(Elements.of(args.group(1)).size());
      }
    }
    return Optional.empty();
  }

  /** The number of top-level arguments of a class pattern that are not keyword patterns. */
  private static int positionalCount(final String arguments) {
    int count = 0;
    for (final String item : Elements.of(arguments)) {
      if (!item.matches("^\\w+=.*")) {
        count++;
      }
    }
    return count;
  }
}
