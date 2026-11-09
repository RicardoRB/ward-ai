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
 * Reports a class pattern with positional sub-patterns for a class of this module that declares
 * {@code __match_args__} or is a dataclass, like pylint {@code match-class-positional-attributes}:
 * keyword attributes are clearer. Classes whose match arguments are unknown are not reported.
 */
public final class MatchPositionalCheck implements RuleCheck {

  private static final Set<String> PATTERNS = Set.of("class_pattern");
  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Pattern CLASS_NAME = Pattern.compile("^(?:async\\s*)?class\\s*(\\w+)");
  private static final Pattern PATTERN_HEAD = Pattern.compile("^(\\w+)\\((?!\\w+=)(?!\\))");
  private static final Pattern MATCH_ARGS = Pattern.compile("__match_args__=");
  private static final Pattern DATACLASS = Pattern.compile("@(?:dataclasses\\.)?dataclass");
  private static final Set<String> SELF_TYPES =
      Set.of(
          "bool",
          "bytearray",
          "bytes",
          "dict",
          "float",
          "frozenset",
          "int",
          "list",
          "set",
          "str",
          "tuple");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public MatchPositionalCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> known = knownMatchClasses(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, PATTERNS)) {
      final Matcher head = PATTERN_HEAD.matcher(SyntaxWalk.text(visit.node()));
      if (head.find() && known.contains(head.group(1)) && !SELF_TYPES.contains(head.group(1))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static Set<String> knownMatchClasses(final SyntaxNode root) {
    final Set<String> known = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final String text = SyntaxWalk.text(visit.node());
      final Matcher name = CLASS_NAME.matcher(text);
      if (name.find() && (MATCH_ARGS.matcher(text).find() || dataclass(visit.ancestors()))) {
        known.add(name.group(1));
      }
    }
    return Set.copyOf(known);
  }

  private static boolean dataclass(final List<SyntaxNode> ancestors) {
    return !ancestors.isEmpty()
        && "decorated".equals(ancestors.get(ancestors.size() - 1).kind())
        && DATACLASS.matcher(SyntaxWalk.text(ancestors.get(ancestors.size() - 1))).find();
  }
}
