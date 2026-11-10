package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports the first variable of a non-const declaration that has neither an initializer nor a type
 * annotation, at the variable, as Biome 2.5.15's {@code noImplicitAnyLet} does in a TypeScript file
 * that is not a definition file. A for-of head is not a statement here, so it is not reported.
 */
public final class BiomeImplicitAnyLetCheck implements RuleCheck {

  private static final String STATEMENT = "variableStatement";
  private static final String LIST = "variableDeclarationList";
  private static final String DECLARATION = "variableDeclaration";
  private static final String MODIFIER = "varModifier";
  private static final String CONST = "const";
  private static final String INITIALIZER = "=";
  private static final String ANNOTATION = "typeAnnotation";
  private static final String DEFINITION_SUFFIX = ".d.ts";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeImplicitAnyLetCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the file kind is read from the unit's path. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the implicit-any check needs the source unit");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    if (unit.path().endsWith(DEFINITION_SUFFIX)) {
      return List.of();
    }
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(statement -> !isConst(statement))
        .flatMap(statement -> untyped(statement).stream())
        .map(declaration -> CheckSupport.at(declaration.span(), message))
        .toList();
  }

  private static boolean isConst(final SyntaxNode statement) {
    return statement.children().stream()
        .filter(child -> MODIFIER.equals(child.kind()))
        .anyMatch(modifier -> CONST.equals(SyntaxWalk.text(modifier)));
  }

  /** The first declaration with neither an initializer nor an annotation, if any. */
  private static Optional<SyntaxNode> untyped(final SyntaxNode statement) {
    return statement.children().stream()
        .filter(child -> LIST.equals(child.kind()))
        .flatMap(list -> list.children().stream())
        .filter(child -> DECLARATION.equals(child.kind()))
        .filter(
            declaration ->
                !hasToken(declaration, INITIALIZER) && !hasChild(declaration, ANNOTATION))
        .findFirst();
  }

  private static boolean hasChild(final SyntaxNode node, final String kind) {
    return node.children().stream().anyMatch(child -> kind.equals(child.kind()));
  }

  /** Whether the node has a token child with this text, such as the = of a declaration. */
  private static boolean hasToken(final SyntaxNode node, final String text) {
    return node.children().stream()
        .anyMatch(child -> child.children().isEmpty() && text.equals(child.fields().get("text")));
  }
}
