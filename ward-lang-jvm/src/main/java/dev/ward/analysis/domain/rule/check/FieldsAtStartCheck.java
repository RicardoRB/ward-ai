package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a field declared after a method, constructor or nested type, as PMD's {@code
 * FieldDeclarationsShouldBeAtStartOfClass} does with its defaults: initializer blocks, empty
 * declarations and nested enums may come before the fields, and a field initialized with an
 * anonymous class is not reported. Interfaces are not checked.
 */
public final class FieldsAtStartCheck implements UnitCheck {

  private static final String MEMBER = "classBodyDeclaration";
  private static final String DECLARATION = "memberDeclaration";
  private static final Set<String> BODIES = Set.of("classBody", "enumBodyDeclarations");
  private static final Set<String> ALLOWED_FIRST =
      Set.of(MemberDeclarations.FIELD, "enumDeclaration");
  private static final String BLOCK = "block";
  private static final String SEMICOLON = "';'";
  private static final String CLASS_BODY = "classBody";
  private static final String NEW = "'new'";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field
   */
  public FieldsAtStartCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit body : SyntaxWalk.visits(root, BODIES)) {
      boolean atStart = true;
      for (final SyntaxNode member : body.node().children()) {
        atStart = atStart && allowedFirst(member);
        if (!atStart && isField(member) && !anonymousInitializer(member)) {
          found.add(CheckSupport.at(member.span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private static boolean allowedFirst(final SyntaxNode member) {
    return !MEMBER.equals(member.kind())
        || declaration(member)
            .map(d -> ALLOWED_FIRST.contains(d.kind()))
            .orElse(isBlockOrEmpty(member));
  }

  private static boolean isBlockOrEmpty(final SyntaxNode member) {
    return member.children().stream()
        .anyMatch(child -> BLOCK.equals(child.kind()) || SEMICOLON.equals(child.kind()));
  }

  private static boolean isField(final SyntaxNode member) {
    return MEMBER.equals(member.kind())
        && declaration(member).map(d -> MemberDeclarations.FIELD.equals(d.kind())).orElse(false);
  }

  private static Optional<SyntaxNode> declaration(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> DECLARATION.equals(child.kind()))
        .flatMap(wrapper -> wrapper.children().stream())
        .findFirst();
  }

  /** A field of one variable whose initializer creates an anonymous class. */
  private static boolean anonymousInitializer(final SyntaxNode member) {
    final List<SyntaxNode> descendants = SyntaxWalk.descendants(member);
    final long declarators =
        descendants.stream().filter(node -> "variableDeclarator".equals(node.kind())).count();
    return declarators == 1
        && descendants.stream().anyMatch(node -> NEW.equals(node.kind()))
        && descendants.stream().anyMatch(node -> CLASS_BODY.equals(node.kind()));
  }
}
