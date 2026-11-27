package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The values of a name that a declaration, a loop or the calls of a function give it. */
final class NodeProgramBindings {
  private static final String DECLARATION = "variableDeclaration";
  private static final String PLAIN_NAME = "identifierOrKeyWord";
  private static final int NAME_LEVELS = 4;

  private NodeProgramBindings() {}

  /** The values of a name declared by {@code const name = value} or by a {@code for...of} loop. */
  static Optional<List<NodeProgramValues.Term>> variable(
      final NodeProgramValues values,
      final NodeProgramFile file,
      final Binding binding,
      final int depth
  ) {
    final Optional<SyntaxNode> declaration = declarationOf(file, binding);
    if (declaration.isEmpty()) {
      return Optional.empty();
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(declaration.get());
    final int last = parts.size() - 1;
    if (!PLAIN_NAME.equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    if (last >= 2 && "=".equals(NodeFlowSyntax.text(parts.get(last - 1)))) {
      return binding.has(BindingFlag.CONST)
          ? values.of(file, parts.get(last), depth + 1)
          : Optional.empty();
    }
    return loopElements(values, file, declaration.get(), depth);
  }

  static Optional<SyntaxNode> declarationOf(final NodeProgramFile file, final Binding binding) {
    Optional<SyntaxNode> node = file.nameAt(binding.declaration());
    for (int level = 0; level < NAME_LEVELS && node.isPresent(); level++) {
      node = node.flatMap(file::parent);
      if (node.isPresent() && DECLARATION.equals(node.get().kind())) {
        return node;
      }
    }
    return Optional.empty();
  }

  private static Optional<List<NodeProgramValues.Term>> loopElements(
      final NodeProgramValues values,
      final NodeProgramFile file,
      final SyntaxNode declaration,
      final int depth
  ) {
    final Optional<SyntaxNode> loop =
        file.parent(declaration)
            .filter(node -> "singleVariableDeclaration".equals(node.kind()))
            .flatMap(file::parent)
            .filter(node -> "iterationStatement".equals(node.kind()));
    if (loop.isEmpty()) {
      return Optional.empty();
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(loop.get());
    final int of = NodeProgramShapes.indexOfToken(parts, "of");
    if (of < 0 || of + 1 >= parts.size()) {
      return Optional.empty();
    }
    return values.access().elements(file, parts.get(of + 1), depth + 1);
  }

  /** The values that every call of the function passes for a parameter. */
  static Optional<List<NodeProgramValues.Term>> parameter(
      final NodeProgramValues values,
      final NodeProgramFile file,
      final Binding binding,
      final int depth
  ) {
    final Optional<NodeProgramFunctions.Calls> calls = NodeProgramFunctions.calls(file, binding);
    if (calls.isEmpty()) {
      return Optional.empty();
    }
    final List<NodeProgramValues.Term> found = new ArrayList<>();
    for (final NodeProgramFunctions.CallSite site : calls.get().sites()) {
      final Optional<List<NodeProgramValues.Term>> passed =
          passed(values, site, calls.get().index(), depth);
      if (passed.isEmpty()) {
        return Optional.empty();
      }
      found.addAll(passed.get());
    }
    return Optional.of(found);
  }

  private static Optional<List<NodeProgramValues.Term>> passed(
      final NodeProgramValues values,
      final NodeProgramFunctions.CallSite site,
      final int index,
      final int depth
  ) {
    final List<SyntaxNode> arguments = site.call().arguments();
    for (int position = 0; position <= index && position < arguments.size(); position++) {
      if (NodeProgramShapes.isSpread(arguments.get(position))) {
        return Optional.empty();
      }
    }
    if (index >= arguments.size()) {
      return Optional.of(List.of());
    }
    return values.of(site.file(), NodeProgramShapes.valueOf(arguments.get(index)), depth + 1);
  }
}
