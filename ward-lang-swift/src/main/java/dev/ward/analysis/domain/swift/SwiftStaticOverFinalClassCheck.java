package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports a {@code class} member of a function, variable or subscript where {@code static} is
 * meant: a {@code final class} member, or a {@code class} member inside a final class, like
 * SwiftLint's {@code static_over_final_class}. Structs and enums do not count as the class context.
 */
public final class SwiftStaticOverFinalClassCheck implements RuleCheck {

  private static final String CLASS = "class_declaration";
  private static final String FINAL = "final";
  private static final String CLASS_KEYWORD = "class";
  private static final Set<String> MEMBERS =
      Set.of("function_declaration", "variable_declaration", "subscript_declaration");
  private static final String HEAD_SUFFIX = "head";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftStaticOverFinalClassCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, null, matches);
    return List.copyOf(matches);
  }

  private void collect(
      final SyntaxNode node, final SyntaxNode enclosingClass, final List<RuleMatch> matches) {
    if (MEMBERS.contains(node.kind())
        && isClassMember(node)
        && isInFinalClass(node, enclosingClass)) {
      matches.add(SwiftTrees.at(node, message));
    }
    final SyntaxNode scope = CLASS.equals(node.kind()) ? node : enclosingClass;
    node.children().forEach(child -> collect(child, scope, matches));
  }

  private static boolean isClassMember(final SyntaxNode member) {
    return heads(member).stream().anyMatch(head -> SwiftTrees.hasToken(head, CLASS_KEYWORD));
  }

  /** A class member is reported when it is itself final or the class around it is final. */
  private static boolean isInFinalClass(final SyntaxNode member, final SyntaxNode enclosingClass) {
    final boolean ownFinal =
        heads(member).stream().anyMatch(head -> SwiftTrees.hasToken(head, FINAL));
    return ownFinal || enclosingClass != null && isFinal(enclosingClass);
  }

  private static boolean isFinal(final SyntaxNode classNode) {
    return classNode.children().stream()
        .filter(child -> !child.kind().endsWith("body"))
        .anyMatch(child -> SwiftTrees.hasToken(child, FINAL));
  }

  private static List<SyntaxNode> heads(final SyntaxNode member) {
    return member.children().stream().filter(child -> child.kind().endsWith(HEAD_SUFFIX)).toList();
  }
}
