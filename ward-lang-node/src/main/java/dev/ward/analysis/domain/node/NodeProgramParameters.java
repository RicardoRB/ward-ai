package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads the parameter list of a function declaration and the bindings of its parameters. */
final class NodeProgramParameters {
  private static final Set<String> PARAMETER_LISTS = Set.of("parameterList", "formalParameterList");
  private static final Set<String> PARAMETERS = Set.of("parameter", "formalParameterArg");
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final String SIGNATURE = "callSignature";

  private NodeProgramParameters() {}

  /** The parameters of a function declaration, or none when one is not a plain name. */
  static Optional<List<SyntaxNode>> parameters(final SyntaxNode function) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(function, found);
    for (final SyntaxNode parameter : found) {
      if (!plain(parameter)) {
        return Optional.empty();
      }
    }
    return Optional.of(found);
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    for (final SyntaxNode child : node.children()) {
      if (PARAMETERS.contains(child.kind())) {
        found.add(child);
      } else if (isList(child) || SIGNATURE.equals(child.kind())) {
        collect(child, found);
      }
    }
  }

  private static boolean isList(final SyntaxNode node) {
    return PARAMETER_LISTS.contains(node.kind());
  }

  /** A parameter that is a name, with no default value, no spread and no pattern. */
  private static boolean plain(final SyntaxNode parameter) {
    return !hasToken(parameter, "=") && !hasToken(parameter, "...") && !hasToken(parameter, "{")
        && !hasToken(parameter, "[");
  }

  private static boolean hasToken(final SyntaxNode node, final String token) {
    if (TYPE_ANNOTATION.equals(node.kind())) {
      return false;
    }
    if (node.children().isEmpty()) {
      return token.equals(NodeFlowSyntax.text(node));
    }
    return node.children().stream().anyMatch(child -> hasToken(child, token));
  }

  static int indexOf(final List<SyntaxNode> parameters, final Span declaration) {
    for (int index = 0; index < parameters.size(); index++) {
      final Span span = parameters.get(index).span();
      if (span.startOffset() <= declaration.startOffset()
          && declaration.endOffset() <= span.endOffset()) {
        return index;
      }
    }
    return -1;
  }

  static Optional<Binding> parameterBinding(
      final NodeProgramFile file,
      final SyntaxNode parameter
  ) {
    final SyntaxNode token = NodeFlowSyntax.firstIdentifier(parameter);
    if (token == null) {
      return Optional.empty();
    }
    return file.scopes().bindings().stream()
        .filter(binding -> binding.kind() == BindingKind.PARAMETER)
        .filter(binding -> binding.declaration().startOffset() == token.span().startOffset())
        .findFirst();
  }
}
