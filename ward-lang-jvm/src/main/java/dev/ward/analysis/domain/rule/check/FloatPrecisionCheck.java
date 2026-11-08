package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reports arithmetic in single precision: a {@code + - * / %} expression, or the compound
 * assignment of one, whose type is {@code float}. Like SpotBugs it is silent in code whose package,
 * class or method name or signature mentions {@code float}, where the precision is intended, and
 * for constant expressions the compiler folds.
 */
public final class FloatPrecisionCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String FLOAT = "float";
  private static final int BINARY = 3;
  private static final Set<String> OPERATORS =
      Set.of("+", "-", "*", "/", "%", "+=", "-=", "*=", "/=", "%=");
  private static final Set<String> SCOPES = Set.of("methodDeclaration", "constructorDeclaration");
  private static final Set<String> TYPE_KINDS =
      Set.of("classDeclaration", "interfaceDeclaration", "enumDeclaration", "recordDeclaration");
  private static final Set<String> BODIES = Set.of("methodBody", "constructorBody", "block");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.CONSTANT));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the expression
   */
  public FloatPrecisionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final String header = packageName(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      if (singlePrecision(visit.node(), types) && !floatNamed(header, visit)) {
        found.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean singlePrecision(final SyntaxNode node, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = node.children();
    return parts.size() == BINARY
        && OPERATORS.contains(SyntaxWalk.text(parts.get(1)))
        && types.constant(node.span()).isEmpty()
        && types
            .typeOf(node.span())
            .filter(type -> type.primitive() && type.arrayDepth() == 0 && FLOAT.equals(type.name()))
            .isPresent();
  }

  private static String packageName(final SyntaxNode root) {
    return SyntaxWalk.descendants(root).stream()
        .filter(node -> "packageDeclaration".equals(node.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  /**
   * Tells whether the package, an enclosing type or the method names float, in any case, or the
   * signature of the method uses a class called {@code Float}.
   */
  private static boolean floatNamed(final String pack, final SyntaxWalk.Visit visit) {
    final StringBuilder name = new StringBuilder(pack);
    final StringBuilder signature = new StringBuilder();
    for (final SyntaxNode outer : visit.ancestors()) {
      if (TYPE_KINDS.contains(outer.kind())) {
        name.append(identifier(outer));
      } else if (SCOPES.contains(outer.kind())) {
        name.append(identifier(outer));
        outer.children().stream()
            .filter(part -> !"identifier".equals(part.kind()))
            .takeWhile(part -> !BODIES.contains(part.kind()))
            .forEach(part -> signature.append(SyntaxWalk.text(part)));
      }
    }
    return name.toString().toLowerCase(Locale.ROOT).contains(FLOAT)
        || signature.toString().contains("Float");
  }

  private static String identifier(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(part -> "identifier".equals(part.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
