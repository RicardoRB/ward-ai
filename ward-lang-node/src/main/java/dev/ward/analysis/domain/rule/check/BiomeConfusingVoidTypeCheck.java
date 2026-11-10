package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a void type that sits in a union or outside a return type and a type parameter, at the
 * void, as Biome 2.5.15's {@code noConfusingVoidType} does. A void is fine as a return type, a type
 * argument, a type parameter default, a function type's result, a this parameter, or the check side
 * of a conditional's extends.
 */
public final class BiomeConfusingVoidTypeCheck implements RuleCheck {

  private static final String PREDEFINED = "predefinedType";
  private static final String VOID = "void";
  private static final String UNION_OR_INTERSECTION = "unionOrIntersectionOrPrimaryType";
  private static final String UNION_TOKEN = "|";
  private static final String ANNOTATION = "typeAnnotation";
  private static final String TYPE = "type_";
  private static final String PRIMARY = "primaryType";
  private static final String CONDITIONAL = "conditionalType";
  private static final String EXTENDS = "extends";
  private static final String THIS = "this";
  private static final String PARAMETER = "requiredParameter";
  private static final String IDENTIFIER_OR_PATTERN = "identifierOrPattern";
  private static final Set<String> NO_REPORT_CONTEXTS =
      Set.of("typeArgument", "typeArgumentList", "typeParameter", "functionType");
  private static final Set<String> SIGNATURES =
      Set.of("callSignature", "arrowFunctionDeclaration", "anonymousFunction", "generatorMethod");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeConfusingVoidTypeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always fails: the void's context is read from the tree and its text from the source. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the void-type check needs the source text");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(PREDEFINED)).stream()
        .filter(visit -> VOID.equals(SyntaxWalk.text(visit.node())))
        .filter(visit -> confusing(visit.node(), visit.ancestors()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** Walks the ancestors outward: a union or an unknown place is confusing, a return is not. */
  private static boolean confusing(final SyntaxNode voidNode, final List<SyntaxNode> ancestors) {
    SyntaxNode child = voidNode;
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode node = ancestors.get(index);
      final Context context = context(node, child);
      if (context != Context.CONTINUE) {
        return context == Context.REPORT;
      }
      child = node;
    }
    return true;
  }

  private enum Context {
    CONTINUE,
    REPORT,
    SILENT
  }

  private static Context context(final SyntaxNode node, final SyntaxNode child) {
    final String kind = node.kind();
    if (UNION_OR_INTERSECTION.equals(kind)) {
      return hasToken(node, UNION_TOKEN) ? Context.REPORT : Context.CONTINUE;
    }
    if (silent(kind, child)) {
      return Context.SILENT;
    }
    if (PARAMETER.equals(kind)) {
      return thisParameter(node) ? Context.SILENT : Context.REPORT;
    }
    if (CONDITIONAL.equals(kind)) {
      return isExtendsOperand(node, child) ? Context.SILENT : Context.CONTINUE;
    }
    return transparent(kind) ? Context.CONTINUE : Context.REPORT;
  }

  /** A type argument, a type parameter, a function type or a return annotation is silent. */
  private static boolean silent(final String kind, final SyntaxNode child) {
    return NO_REPORT_CONTEXTS.contains(kind) || SIGNATURES.contains(kind) && isAnnotation(child);
  }

  /** The wrappers a void sits inside on its way to the place it is written. */
  private static boolean transparent(final String kind) {
    return ANNOTATION.equals(kind) || TYPE.equals(kind) || PRIMARY.equals(kind);
  }

  private static boolean isAnnotation(final SyntaxNode node) {
    return ANNOTATION.equals(node.kind());
  }

  private static boolean hasToken(final SyntaxNode node, final String text) {
    return node.children().stream()
        .anyMatch(child -> child.children().isEmpty() && text.equals(child.fields().get("text")));
  }

  private static boolean thisParameter(final SyntaxNode parameter) {
    return parameter.children().stream()
        .filter(child -> IDENTIFIER_OR_PATTERN.equals(child.kind()))
        .anyMatch(child -> THIS.equals(SyntaxWalk.text(child)));
  }

  /** The operand that follows the extends token of a conditional type. */
  private static boolean isExtendsOperand(final SyntaxNode conditional, final SyntaxNode child) {
    final List<SyntaxNode> children = conditional.children();
    final int position = children.indexOf(child);
    return position > 0 && EXTENDS.equals(children.get(position - 1).fields().get("text"));
  }
}
