package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each statement terminator that ends on a line where an earlier terminator already ends,
 * as Checkstyle's OneStatementPerLine does. The semicolons of a for header and the empty member
 * declarations are not statements; the resources of a try statement are, when the user option says
 * so.
 */
public final class OneStatementPerLineCheck implements UnitCheck {

  private static final String SEMICOLON = "';'";
  private static final String RESOURCE = "resource";
  private static final Set<String> FOR_HEADER = Set.of("forControl");
  private static final Set<String> RESOURCE_LISTS = Set.of("resourceSpecification", "resources");
  private static final Set<String> MEMBER_LISTS =
      Set.of("classBodyDeclaration", "interfaceBodyDeclaration", "enumBodyDeclarations");

  private final boolean treatTryResourcesAsStatement;
  private final String message;

  /**
   * Creates the check.
   *
   * @param treatTryResourcesAsStatement whether the resources of a try statement count as
   *     statements
   * @param message the message reported at each extra statement
   */
  public OneStatementPerLineCheck(
      final boolean treatTryResourcesAsStatement, final String message) {
    this.treatTryResourcesAsStatement = treatTryResourcesAsStatement;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<SyntaxNode> terminators = new ArrayList<>();
    collect(root, false, terminators);
    final List<RuleMatch> matches = new ArrayList<>();
    int previousLine = -1;
    for (final SyntaxNode terminator : terminators) {
      final int line = terminator.span().endLine();
      if (line == previousLine) {
        matches.add(CheckSupport.at(terminator.span(), message));
      }
      previousLine = line;
    }
    return List.copyOf(matches);
  }

  private void collect(
      final SyntaxNode node, final boolean inResources, final List<SyntaxNode> terminators) {
    final String kind = node.kind();
    if (FOR_HEADER.contains(kind)) {
      return;
    }
    if (RESOURCE.equals(kind)) {
      if (treatTryResourcesAsStatement) {
        terminators.add(node);
      }
      return;
    }
    if (SEMICOLON.equals(kind)) {
      if (!inResources) {
        terminators.add(node);
      }
      return;
    }
    final boolean resources = inResources || RESOURCE_LISTS.contains(kind);
    final boolean members = MEMBER_LISTS.contains(kind);
    for (final SyntaxNode child : node.children()) {
      if (!members || !SEMICOLON.equals(child.kind())) {
        collect(child, resources, terminators);
      }
    }
  }
}
