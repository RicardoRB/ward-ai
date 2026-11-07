package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each private, non-final, non-static field that no method assigns, so it could be final.
 * Constructors and initializers may assign it.
 */
public final class ImmutableFieldCheck implements UnitCheck {

  private static final String IDENTIFIER = "identifier";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String EXPRESSION = "expression";
  private static final String ASSIGNMENT_TEMPLATE =
      "^(this\\.)?%s(=(?!=)|[-+*/%%&|^]=|>>>?=|<<=|\\+\\+|--)|^(\\+\\+|--)(this\\.)?%s$";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field declaration
   */
  public ImmutableFieldCheck(final String message) {
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
    final List<SyntaxNode> methods =
        members.stream()
            .flatMap(member -> MemberDeclarations.declaration(member).stream())
            .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
            .toList();
    return members.stream()
        .filter(ImmutableFieldCheck::isPrivateInstanceField)
        .flatMap(field -> MemberDeclarations.declaration(field).stream())
        .filter(field -> names(field).stream().noneMatch(name -> isAssignedIn(name, methods)))
        .map(field -> CheckSupport.at(field.span(), message))
        .toList();
  }

  private static boolean isPrivateInstanceField(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return modifiers.contains(MemberDeclarations.PRIVATE)
        && !modifiers.contains(MemberDeclarations.FINAL)
        && !modifiers.contains(MemberDeclarations.STATIC)
        && MemberDeclarations.declaration(member)
            .map(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
            .orElse(false);
  }

  private static List<String> names(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(node -> DECLARATOR_ID.equals(node.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }

  private static boolean isAssignedIn(final String name, final List<SyntaxNode> methods) {
    final Pattern assignment =
        Pattern.compile(
            String.format(ASSIGNMENT_TEMPLATE, Pattern.quote(name), Pattern.quote(name)));
    return methods.stream()
        .flatMap(method -> SyntaxWalk.descendants(method).stream())
        .filter(node -> EXPRESSION.equals(node.kind()))
        .anyMatch(node -> assignment.matcher(SyntaxWalk.text(node)).find());
  }
}
