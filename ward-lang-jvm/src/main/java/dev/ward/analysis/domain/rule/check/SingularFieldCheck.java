package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each private field that only one method uses, and that method assigns: the field could be
 * a local variable. Approximation: any assignment in that method counts as assigning first.
 */
public final class SingularFieldCheck implements UnitCheck {

  private static final String IDENTIFIER = "identifier";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String EXPRESSION = "expression";
  private static final String ASSIGNMENT_TEMPLATE = "^(this\\.)?%s(=(?!=)|[-+*/%%&|^]=|>>>?=|<<=)";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field declaration
   */
  public SingularFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> MemberDeclarations.body(clazz).stream())
        .flatMap(body -> reportOf(body).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode body) {
    final List<SyntaxNode> members = MemberDeclarations.members(body);
    return members.stream()
        .filter(SingularFieldCheck::isPrivateField)
        .flatMap(field -> MemberDeclarations.declaration(field).stream())
        .filter(field -> names(field).stream().anyMatch(name -> isSingular(name, field, members)))
        .map(field -> CheckSupport.at(field.span(), message))
        .toList();
  }

  private static boolean isPrivateField(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).contains(MemberDeclarations.PRIVATE)
        && MemberDeclarations.declaration(member)
            .map(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
            .orElse(false);
  }

  private static boolean isSingular(
      final String name, final SyntaxNode field, final List<SyntaxNode> members) {
    final List<SyntaxNode> users =
        members.stream()
            .flatMap(member -> MemberDeclarations.declaration(member).stream())
            .filter(declaration -> !declaration.equals(field))
            .filter(declaration -> usesName(declaration, name))
            .toList();
    return users.size() == 1
        && MemberDeclarations.METHOD.equals(users.get(0).kind())
        && assigns(users.get(0), name);
  }

  private static boolean usesName(final SyntaxNode declaration, final String name) {
    return SyntaxWalk.descendants(declaration).stream()
        .anyMatch(node -> IDENTIFIER.equals(node.kind()) && name.equals(SyntaxWalk.text(node)));
  }

  private static boolean assigns(final SyntaxNode method, final String name) {
    final Pattern assignment =
        Pattern.compile(String.format(ASSIGNMENT_TEMPLATE, Pattern.quote(name)));
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> EXPRESSION.equals(node.kind()))
        .anyMatch(node -> assignment.matcher(SyntaxWalk.text(node)).find());
  }

  private static List<String> names(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(node -> DECLARATOR_ID.equals(node.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }
}
