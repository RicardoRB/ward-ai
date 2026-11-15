package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports each assignment of {@code null} to a variable, as PMD's NullAssignment does, except the
 * assignment of a {@code final} field of the enclosing class, which is how a constructor leaves
 * that field empty.
 */
public final class NullAssignmentCheck implements UnitCheck {
  private static final Pattern NULL_ASSIGNMENT = Pattern.compile("^([\\w.\\[\\]]+)=null$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NullAssignmentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      final Matcher target = NULL_ASSIGNMENT.matcher(SyntaxWalk.text(visit.node()));
      if (target.find() && !assignsFinalField(target.group(1), visit.ancestors())) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean assignsFinalField(final String target, final List<SyntaxNode> ancestors) {
    final String name = target.substring(target.lastIndexOf('.') + 1);
    return ancestors.stream()
        .filter(node -> MemberDeclarations.CLASS.equals(node.kind()))
        .anyMatch(clazz -> declaresFinal(clazz, name));
  }

  private static boolean declaresFinal(final SyntaxNode clazz, final String name) {
    return ClassMembers.fieldMembers(clazz).stream()
        .filter(field -> MemberDeclarations.modifiers(field).contains(MemberDeclarations.FINAL))
        .anyMatch(
            field -> ClassMembers.fieldNames(ClassMembers.declarationOf(field)).contains(name)
        );
  }
}
