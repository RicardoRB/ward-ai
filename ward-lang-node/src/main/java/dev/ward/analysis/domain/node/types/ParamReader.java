package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Helpers split out of {@link SigReader}. */
final class ParamReader {
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final String INITIALIZER = "initializer";
  private static final String QUESTION = "?";
  private static final Set<String> OPTIONAL_PARAMETER_KINDS =
      Set.of("optionalParameter", "formalParameterArg");
  private static final Set<String> REST_KINDS = Set.of("restParameter", "lastFormalParameterArg");

  private ParamReader() {}

  static List<Param> arrowParams(final SyntaxNode arrow) {
    final SyntaxNode list = TreeKit.child(arrow, "formalParameterList");
    if (list != null) {
      return SigReader.params(arrow);
    }
    final SyntaxNode single = TreeKit.child(arrow, "propertyName");
    return single == null
        ? List.of()
        : List.of(new Param(TreeText.text(single), TsType.UNKNOWN, false, false));
  }

  static SyntaxNode firstPart(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    return parts.isEmpty() ? null : parts.get(0);
  }

  static boolean isParameter(final SyntaxNode node) {
    return switch (node.kind()) {
      case "requiredParameter",
          "optionalParameter",
          "restParameter",
          "formalParameterArg",
          "lastFormalParameterArg" ->
          true;
      default -> false;
    };
  }

  static Param param(final SyntaxNode node) {
    final boolean rest = REST_KINDS.contains(node.kind());
    final String name = parameterName(node);
    final SyntaxNode initializer = TreeKit.child(node, INITIALIZER);
    final boolean hasDefault = initializer != null || TreeKit.hasToken(node, "=");
    final boolean optional =
        OPTIONAL_PARAMETER_KINDS.contains(node.kind())
            && (TreeKit.hasToken(node, QUESTION) || hasDefault);
    TsType type = SigReader.annotated(TreeKit.child(node, TYPE_ANNOTATION));
    if (!type.isKnown() && TreeKit.child(node, TYPE_ANNOTATION) == null) {
      type = defaultType(node, initializer);
    }
    return new Param(name, type, optional, rest);
  }

  static TsType defaultType(final SyntaxNode node, final SyntaxNode initializer) {
    final SyntaxNode value =
        initializer != null ? TreeKit.child(initializer, "singleExpression") : lastExpression(node);
    final SyntaxNode literal = value == null ? null : TreeKit.child(value, "literal");
    return literal == null
        ? TsType.UNKNOWN
        : Literals.of(literal).map(Literals::widen).orElse(TsType.UNKNOWN);
  }

  static SyntaxNode lastExpression(final SyntaxNode node) {
    final List<SyntaxNode> expressions = TreeKit.children(node, "singleExpression");
    return expressions.isEmpty() || !TreeKit.hasToken(node, "=")
        ? null
        : expressions.get(expressions.size() - 1);
  }

  static String parameterName(final SyntaxNode node) {
    for (final SyntaxNode part : TreeKit.parts(node)) {
      switch (part.kind()) {
        case "identifierOrPattern", "assignable", "identifier", "singleExpression" -> {
          final String text = TreeText.text(part);
          return isIdentifier(text) ? text : "arg";
        }
        default -> {
          // modifiers, decorators and punctuation do not name the parameter
        }
      }
    }
    return "arg";
  }

  static boolean isIdentifier(final String text) {
    return !text.isEmpty()
        && (Character.isJavaIdentifierStart(text.charAt(0)))
        && text.chars().allMatch(Character::isJavaIdentifierPart);
  }
}
