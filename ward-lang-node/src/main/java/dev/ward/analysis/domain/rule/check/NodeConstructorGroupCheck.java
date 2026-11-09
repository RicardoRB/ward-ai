package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reports each constructor that comes after another member which itself followed a constructor, so
 * the constructors of a class are not grouped together, like Checkstyle's
 * ConstructorsDeclarationGrouping.
 */
public final class NodeConstructorGroupCheck implements RuleCheck {

  private static final String BODY = "classTail";
  private static final String MEMBER = "classElement";
  private static final Pattern CONSTRUCTOR = Pattern.compile("^(?:public|private|protected)?constructor\\(");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeConstructorGroupCheck(final String message) {
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
    boolean constructed = false;
    boolean leftGroup = false;
    for (final SyntaxNode member : NodeSyntax.code(body)) {
      if (!MEMBER.equals(member.kind())) {
        continue;
      }
      final boolean constructor = CONSTRUCTOR.matcher(NodeSyntax.text(member)).find();
      if (constructor && leftGroup) {
        matches.add(new RuleMatch(member.span(), message));
      }
      if (constructor) {
        constructed = true;
      } else if (constructed) {
        leftGroup = true;
      }
    }
    return matches;
  }
}
