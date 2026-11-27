package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The functions of a file whose every call the file shows: a function declaration that is not
 * exported and is never used except by calling it by name. Only for such a function does the file
 * know all the values its parameters can take.
 */
final class NodeProgramFunctions {
  private static final String FUNCTION = "functionDeclaration";
  private static final int NAME_LEVELS = 3;
  private static final Set<String> OTHER_FUNCTIONS =
      Set.of(
          "arrowFunctionDeclaration",
          "anonymousFunction",
          "methodDefinition",
          "constructorDeclaration",
          "getAccessor",
          "setAccessor",
          "propertyMemberDeclaration"
      );

  private NodeProgramFunctions() {}

  /** One call of a function, with the file it is in. */
  record CallSite(NodeProgramFile file, NodeProgramShapes.Call call) {}

  /**
   * The calls of the function that declares {@code parameter}. The
   * function must be called at least once and used in no other way.
   *
   * @return the calls, or none when the function may be called from somewhere unseen
   */
  static Optional<Calls> calls(final NodeProgramFile file, final Binding parameter) {
    return functionOf(file, parameter).flatMap(function -> callsOf(file, function));
  }

  /**
   * The calls of a function and the position of the parameter in its plain parameter list.
   *
   * @param index the position of the parameter
   * @param sites the calls
   */
  record Calls(int index, List<CallSite> sites) {
    /** Copies the calls. */
    Calls {
      sites = List.copyOf(sites);
    }
  }

  /** The parameter binding that the call hands its {@code index}-th argument to, if known. */
  static Optional<Binding> parameterFor(
      final NodeProgramFile file,
      final SyntaxNode callee,
      final int index
  ) {
    return NodeProgramShapes.name(callee)
        .flatMap(file::bindingOf)
        .filter(binding -> binding.kind() == BindingKind.FUNCTION)
        .flatMap(binding -> declaration(file, binding))
        .flatMap(NodeProgramParameters::parameters)
        .filter(list -> index < list.size())
        .flatMap(list -> NodeProgramParameters.parameterBinding(file, list.get(index)));
  }

  private record Located(SyntaxNode node, Binding binding, int index) {}

  private static Optional<Located> functionOf(final NodeProgramFile file, final Binding parameter) {
    if (parameter.kind() != BindingKind.PARAMETER || parameter.has(BindingFlag.DESTRUCTURED)) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> name = file.nameAt(parameter.declaration());
    final Optional<SyntaxNode> function = name.flatMap(token -> enclosing(file, token));
    if (function.isEmpty()) {
      return Optional.empty();
    }
    final Optional<Binding> binding = functionBinding(file, function.get());
    final Optional<List<SyntaxNode>> params = NodeProgramParameters.parameters(function.get());
    if (binding.isEmpty() || params.isEmpty()) {
      return Optional.empty();
    }
    final int index = NodeProgramParameters.indexOf(params.get(), parameter.declaration());
    return index < 0
        ? Optional.empty()
        : Optional.of(new Located(function.get(), binding.get(), index));
  }

  private static Optional<Calls> callsOf(final NodeProgramFile file, final Located function) {
    if (!isPrivate(file, function) || reads(function.node(), "arguments")) {
      return Optional.empty();
    }
    final List<CallSite> sites = new ArrayList<>();
    for (final NameReference use : file.scopes().usesOf(function.binding())) {
      if (use.span().startOffset() == function.binding().declaration().startOffset()) {
        continue;
      }
      final Optional<NodeProgramShapes.Call> call = callOf(file, use.span());
      if (call.isEmpty()) {
        return Optional.empty();
      }
      sites.add(new CallSite(file, call.get()));
    }
    return sites.isEmpty() ? Optional.empty() : Optional.of(new Calls(function.index(), sites));
  }

  /** Whether a name token appears under the node. */
  private static boolean reads(final SyntaxNode node, final String name) {
    if (node.children().isEmpty()) {
      return name.equals(NodeFlowSyntax.text(node));
    }
    return node.children().stream().anyMatch(child -> reads(child, name));
  }

  private static boolean isPrivate(final NodeProgramFile file, final Located function) {
    return !function.binding().has(BindingFlag.EXPORTED) && !file.exported(function.node());
  }

  /** The call that has the name at the span as its callee, if the name is only called. */
  private static Optional<NodeProgramShapes.Call> callOf(
      final NodeProgramFile file,
      final Span span
  ) {
    final Optional<SyntaxNode> token = file.nameAt(span);
    Optional<SyntaxNode> node = token;
    for (int up = 0; up < NAME_LEVELS && node.isPresent(); up++) {
      node = node.flatMap(file::parent);
    }
    final Optional<SyntaxNode> expression = node.filter(
        candidate -> NodeProgramShapes.name(candidate).isPresent()
    );
    return expression
        .flatMap(file::parent)
        .flatMap(
            parent -> NodeProgramShapes.call(parent).filter(
                call -> call.callee() == expression.get()
            )
        );
  }

  private static Optional<SyntaxNode> enclosing(
      final NodeProgramFile file,
      final SyntaxNode token
  ) {
    Optional<SyntaxNode> node = file.parent(token);
    while (node.isPresent()) {
      if (FUNCTION.equals(node.get().kind())) {
        return node;
      }
      if (OTHER_FUNCTIONS.contains(node.get().kind())) {
        return Optional.empty();
      }
      node = node.flatMap(file::parent);
    }
    return Optional.empty();
  }

  private static Optional<SyntaxNode> declaration(
      final NodeProgramFile file,
      final Binding function
  ) {
    return file.nameAt(function.declaration())
        .flatMap(file::parent)
        .flatMap(file::parent)
        .filter(node -> FUNCTION.equals(node.kind()));
  }

  private static Optional<Binding> functionBinding(
      final NodeProgramFile file,
      final SyntaxNode function
  ) {
    final SyntaxNode name = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(function), "identifier");
    final SyntaxNode token = name == null ? null : NodeFlowSyntax.firstIdentifier(name);
    if (token == null) {
      return Optional.empty();
    }
    return file.scopes().bindings().stream()
        .filter(binding -> binding.kind() == BindingKind.FUNCTION)
        .filter(binding -> binding.declaration().startOffset() == token.span().startOffset())
        .findFirst();
  }
}
