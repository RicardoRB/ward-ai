package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads signatures from call signatures, function nodes and parameter lists. */
final class SigReader {
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final String TYPE = "type_";
  private static final String INITIALIZER = "initializer";
  private static final String QUESTION = "?";
  private static final Set<String> OPTIONAL_PARAMETER_KINDS =
      Set.of("optionalParameter", "formalParameterArg");
  private static final Set<String> REST_KINDS = Set.of("restParameter", "lastFormalParameterArg");

  private SigReader() {}

  /** Returns the signature whose type parameters, parameters and return type are under holder. */
  static Sig of(final SyntaxNode holder) {
    return new Sig(
        typeParams(TreeKit.child(holder, "typeParameters")), params(holder), returns(holder));
  }

  /** Returns the type declared by an annotation node, or {@link Ty#UNKNOWN}. */
  static Ty annotated(final SyntaxNode annotation) {
    if (annotation == null) {
      return Ty.UNKNOWN;
    }
    final SyntaxNode type = TreeKit.child(annotation, TYPE);
    return type == null ? Ty.UNKNOWN : TyParser.parse(TreeKit.leaves(type));
  }

  /** Returns the return type declared under {@code holder}. */
  static Ty returns(final SyntaxNode holder) {
    return annotated(TreeKit.child(holder, TYPE_ANNOTATION));
  }

  /** Returns the names of the type parameters in a {@code typeParameters} node. */
  static List<String> typeParams(final SyntaxNode node) {
    final List<String> names = new ArrayList<>();
    if (node != null) {
      final SyntaxNode list = TreeKit.child(node, "typeParameterList");
      if (list != null) {
        for (final SyntaxNode parameter : TreeKit.children(list, "typeParameter")) {
          final SyntaxNode identifier = TreeKit.child(parameter, "identifier");
          if (identifier != null) {
            names.add(TreeKit.text(identifier));
          }
        }
      }
    }
    return names;
  }

  /** Returns the parameters declared under {@code holder}. */
  static List<Param> params(final SyntaxNode holder) {
    SyntaxNode list = TreeKit.child(holder, "parameterList");
    if (list == null) {
      list = TreeKit.child(holder, "formalParameterList");
    }
    if (list == null) {
      final SyntaxNode arrow = TreeKit.child(holder, "arrowFunctionParameters");
      return arrow == null ? List.of() : arrowParams(arrow);
    }
    final List<Param> params = new ArrayList<>();
    for (final SyntaxNode part : TreeKit.parts(list)) {
      final SyntaxNode parameter = "parameter".equals(part.kind()) ? firstPart(part) : part;
      if (parameter != null && isParameter(parameter)) {
        params.add(param(parameter));
      }
    }
    return params;
  }

  private static List<Param> arrowParams(final SyntaxNode arrow) {
    final SyntaxNode list = TreeKit.child(arrow, "formalParameterList");
    if (list != null) {
      return params(arrow);
    }
    final SyntaxNode single = TreeKit.child(arrow, "propertyName");
    return single == null
        ? List.of()
        : List.of(new Param(TreeKit.text(single), Ty.UNKNOWN, false, false));
  }

  private static SyntaxNode firstPart(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    return parts.isEmpty() ? null : parts.get(0);
  }

  private static boolean isParameter(final SyntaxNode node) {
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

  private static Param param(final SyntaxNode node) {
    final boolean rest = REST_KINDS.contains(node.kind());
    final String name = parameterName(node);
    final SyntaxNode initializer = TreeKit.child(node, INITIALIZER);
    final boolean hasDefault = initializer != null || TreeKit.hasToken(node, "=");
    final boolean optional =
        OPTIONAL_PARAMETER_KINDS.contains(node.kind())
            && (TreeKit.hasToken(node, QUESTION) || hasDefault);
    Ty type = annotated(TreeKit.child(node, TYPE_ANNOTATION));
    if (!type.isKnown() && TreeKit.child(node, TYPE_ANNOTATION) == null) {
      type = defaultType(node, initializer);
    }
    return new Param(name, type, optional, rest);
  }

  private static Ty defaultType(final SyntaxNode node, final SyntaxNode initializer) {
    final SyntaxNode value =
        initializer != null ? TreeKit.child(initializer, "singleExpression") : lastExpression(node);
    final SyntaxNode literal = value == null ? null : TreeKit.child(value, "literal");
    return literal == null
        ? Ty.UNKNOWN
        : Literals.of(literal).map(Literals::widen).orElse(Ty.UNKNOWN);
  }

  private static SyntaxNode lastExpression(final SyntaxNode node) {
    final List<SyntaxNode> expressions = TreeKit.children(node, "singleExpression");
    return expressions.isEmpty() || !TreeKit.hasToken(node, "=")
        ? null
        : expressions.get(expressions.size() - 1);
  }

  private static String parameterName(final SyntaxNode node) {
    for (final SyntaxNode part : TreeKit.parts(node)) {
      switch (part.kind()) {
        case "identifierOrPattern", "assignable", "identifier", "singleExpression" -> {
          final String text = TreeKit.text(part);
          return isIdentifier(text) ? text : "arg";
        }
        default -> {
          // modifiers, decorators and punctuation do not name the parameter
        }
      }
    }
    return "arg";
  }

  private static boolean isIdentifier(final String text) {
    return !text.isEmpty()
        && (Character.isJavaIdentifierStart(text.charAt(0)))
        && text.chars().allMatch(Character::isJavaIdentifierPart);
  }
}
