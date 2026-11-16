package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports each class member whose name was already declared by an earlier member that is not the
 * one just before it, so overloads (and overload signatures) are not adjacent. Used by the
 * overload-order rules of Checkstyle and typescript-eslint.
 */
public final class NodeOverloadGroupingCheck implements RuleCheck {
  private static final String BODY = "classTail";
  private static final String MEMBER = "classElement";
  private static final Set<String> MODIFIERS =
      Set.of(
          "public",
          "private",
          "protected",
          "static",
          "readonly",
          "abstract",
          "override",
          "async",
          "declare",
          "accessor"
      );
  private static final Set<String> ACCESSORS = Set.of("get", "set");
  private static final String PRIVATE = "#";
  private static final String NAME = "[A-Za-z_$][\\w$]*";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeOverloadGroupingCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (BODY.equals(node.kind())) {
      matches.addAll(misplaced(node));
    }
    node.children().forEach(child -> collect(child, matches));
  }

  private List<RuleMatch> misplaced(final SyntaxNode body) {
    final List<RuleMatch> matches = new ArrayList<>();
    final Set<String> declared = new HashSet<>();
    String previous = "";
    for (final SyntaxNode member : NodeSyntax.code(body)) {
      if (!MEMBER.equals(member.kind())) {
        continue;
      }
      final String name = name(member);
      if (name.isEmpty()) {
        previous = "";
        continue;
      }
      if (declared.contains(name) && !name.equals(previous)) {
        matches.add(new RuleMatch(member.span(), message));
      }
      declared.add(name);
      previous = name;
    }
    return matches;
  }

  /**
   * The first identifier of the member that is not a modifier, or empty when it has none. The
   * {@code get} or {@code set} before the name of an accessor is not the name: a getter and the
   * setter of the same property are one group, and two getters are not. A private name keeps its
   * {@code #}, so {@code #size} and {@code size} are two names.
   */
  private static String name(final SyntaxNode member) {
    final List<String> tokens = leaves(member);
    for (int index = 0; index < tokens.size(); index++) {
      final String token = tokens.get(index);
      final boolean accessor =
          ACCESSORS.contains(token)
              && index + 1 < tokens.size()
              && tokens.get(index + 1).matches(NAME);
      if (PRIVATE.equals(token) && index + 1 < tokens.size()) {
        return PRIVATE + tokens.get(index + 1);
      }
      if (!accessor && !MODIFIERS.contains(token) && token.matches(NAME)) {
        return token;
      }
    }
    return "";
  }

  private static List<String> leaves(final SyntaxNode node) {
    final List<String> out = new ArrayList<>();
    collectLeaves(node, out);
    return out;
  }

  private static void collectLeaves(final SyntaxNode node, final List<String> out) {
    if (node.children().isEmpty()) {
      out.add(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> collectLeaves(child, out));
  }
}
