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

  /** Returns the type declared by an annotation node, or {@link TsType#UNKNOWN}. */
  static TsType annotated(final SyntaxNode annotation) {
    if (annotation == null) {
      return TsType.UNKNOWN;
    }
    final SyntaxNode type = TreeKit.child(annotation, TYPE);
    return type == null ? TsType.UNKNOWN : TyParser.parse(TreeText.leaves(type));
  }

  /** Returns the return type declared under {@code holder}. */
  static TsType returns(final SyntaxNode holder) {
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
            names.add(TreeText.text(identifier));
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
      return arrow == null ? List.of() : ParamReader.arrowParams(arrow);
    }
    final List<Param> params = new ArrayList<>();
    for (final SyntaxNode part : TreeKit.parts(list)) {
      final SyntaxNode parameter =
          "parameter".equals(part.kind()) ? ParamReader.firstPart(part) : part;
      if (parameter != null && ParamReader.isParameter(parameter)) {
        params.add(ParamReader.param(parameter));
      }
    }
    return params;
  }
}
