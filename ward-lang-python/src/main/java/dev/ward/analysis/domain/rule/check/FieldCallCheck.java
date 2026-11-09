package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code dataclasses.field()} call that is not the value of an annotated assignment in
 * the body of a {@code @dataclass} class, and not an argument of {@code make_dataclass()}, like
 * pylint {@code invalid-field-call}.
 */
public final class FieldCallCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern FIELD = Pattern.compile("^(?:dataclasses\\.)?field\\(");
  private static final Pattern MAKE_DATACLASS = Pattern.compile("^make_dataclass\\(");
  private static final Pattern DATACLASS = Pattern.compile("@(?:dataclasses\\.)?dataclass");
  private static final Pattern FIELD_VALUE =
      Pattern.compile("^[A-Za-z_]\\w*:[^=]*=(?:dataclasses\\.)?field\\(");
  private static final Set<String> STATEMENTS = Set.of("annassign", "expr_stmt", "simple_stmt");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public FieldCallCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      if (FIELD.matcher(SyntaxWalk.text(visit.node())).find() && !valid(visit.ancestors())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Valid inside make_dataclass() or in the annotated field assignments of a dataclass body. */
  private static boolean valid(final List<SyntaxNode> ancestors) {
    for (final SyntaxNode node : ancestors) {
      if ("atom_expr".equals(node.kind()) && MAKE_DATACLASS.matcher(SyntaxWalk.text(node)).find()) {
        return true;
      }
    }
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if ("classdef".equals(ancestors.get(index).kind())) {
        return dataclassBody(ancestors, index);
      }
    }
    return false;
  }

  private static boolean dataclassBody(final List<SyntaxNode> ancestors, final int classIndex) {
    final String classText = SyntaxWalk.text(ancestors.get(classIndex));
    final String header =
        classText.startsWith("@")
            ? classText.substring(0, Math.max(0, classText.indexOf("class")))
            : "";
    final boolean decorated =
        DATACLASS.matcher(header).find()
            || (classIndex > 0
                && "decorated".equals(ancestors.get(classIndex - 1).kind())
                && DATACLASS.matcher(SyntaxWalk.text(ancestors.get(classIndex - 1))).find());
    return decorated && assignmentStatement(ancestors, classIndex);
  }

  /** True when an ancestor below the class is the annotated field assignment of this call. */
  private static boolean assignmentStatement(
      final List<SyntaxNode> ancestors, final int classIndex) {
    for (int index = ancestors.size() - 1; index > classIndex; index--) {
      if (FIELD_VALUE.matcher(SyntaxWalk.text(ancestors.get(index))).find()) {
        return true;
      }
    }
    return false;
  }
}
