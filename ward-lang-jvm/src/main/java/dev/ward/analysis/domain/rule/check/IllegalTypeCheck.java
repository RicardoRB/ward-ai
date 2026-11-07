package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each variable, field, parameter and method return type whose class name is one of the
 * illegal names, as Checkstyle's IllegalType does: a concrete collection type such as {@code
 * HashMap} should be declared as its interface. The name is compared without its type arguments,
 * with the simple or the qualified spelling as the user lists it.
 *
 * <p>Shortcut: the check reads the declared type's text, so a type that an import renames is not
 * recognized, and {@code var} declarations are never reported.
 */
public final class IllegalTypeCheck implements UnitCheck {

  private static final Set<String> DECLARATIONS =
      Set.of(
          "localVariableDeclaration", "fieldDeclaration", "formalParameter", "methodDeclaration");
  private static final Set<String> NOT_ENTERED = Set.of("block", "classBody");
  private static final String METHOD = "methodDeclaration";

  private final Set<String> illegalNames;
  private final String message;

  /**
   * Creates the check.
   *
   * @param illegalNames the class names a declaration may not use, simple or qualified
   * @param message the message of each finding; {@code {value}} stands for the type's name
   */
  public IllegalTypeCheck(final Set<String> illegalNames, final String message) {
    this.illegalNames = Set.copyOf(Objects.requireNonNull(illegalNames, "illegalNames"));
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, DECLARATIONS)) {
      declaredType(visit.node())
          .filter(type -> illegalNames.contains(rawName(SyntaxWalk.text(type))))
          .ifPresent(
              type ->
                  matches.add(
                      CheckSupport.at(
                          SyntaxWalk.leaves(type).get(0).span(),
                          CheckSupport.fill(message, rawName(SyntaxWalk.text(type)), ""))));
    }
    return List.copyOf(matches);
  }

  private static Optional<SyntaxNode> declaredType(final SyntaxNode declaration) {
    if (METHOD.equals(declaration.kind())) {
      return declaration.children().stream()
          .filter(child -> "typeTypeOrVoid".equals(child.kind()))
          .findFirst();
    }
    return SyntaxWalk.firstDescendant(declaration, "typeType", NOT_ENTERED);
  }

  private static String rawName(final String type) {
    final int generics = type.indexOf('<');
    final String withoutGenerics = generics < 0 ? type : type.substring(0, generics);
    final int array = withoutGenerics.indexOf('[');
    return array < 0 ? withoutGenerics : withoutGenerics.substring(0, array);
  }
}
