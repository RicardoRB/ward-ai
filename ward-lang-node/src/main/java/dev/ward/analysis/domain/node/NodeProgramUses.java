package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decides what a use of an array or an object does when it is not a plain read of a member: a
 * call of an element accessor or of a function the data holds, an argument, a declaration, a loop
 * and a literal that stores the value.
 */
final class NodeProgramUses {
  private static final Set<String> ELEMENT_METHODS = Set.of("find", "findLast", "at");
  private static final String PROPERTY = "propertyAssignment";
  private static final String ANONYMOUS = "anonymousFunction";
  private static final String SHORTHAND = "identifierOrKeyWord";
  private static final String DECLARATION = "variableDeclaration";
  private static final int LITERAL_LEVELS = 3;

  private final NodeProgramValues values;
  private final NodeProgramSeal seal;

  NodeProgramUses(final NodeProgramValues values, final NodeProgramSeal seal) {
    this.values = values;
    this.seal = seal;
  }

  /** The value is called, or a member of it is: an element accessor, or a function it holds. */
  boolean called(
      final NodeProgramFile file,
      final SyntaxNode callee,
      final SyntaxNode call,
      final Predicate<SyntaxNode> onward
  ) {
    final Optional<NodeProgramShapes.Member> member = NodeProgramShapes.member(callee);
    final Optional<NodeProgramShapes.Call> shape = NodeProgramShapes.call(call);
    if (member.isEmpty() || shape.isEmpty()) {
      return false;
    }
    if (ELEMENT_METHODS.contains(member.get().name())) {
      return callbackSealed(file, shape.get()) && onward.test(call);
    }
    return holdsFunctions(file, member.get());
  }

  /** Whether each value of the property is a function that the data holds, or a call making one. */
  private boolean holdsFunctions(
      final NodeProgramFile file,
      final NodeProgramShapes.Member member
  ) {
    final NodeProgramValues scratch = values.scratch();
    final Optional<List<NodeProgramValues.Term>> properties =
        scratch.access().properties(file, member, 0);
    return scratch.reentries() == 0
        && properties.isPresent()
        && properties.get().stream().allMatch(NodeProgramUses::function);
  }

  /**
   * A function the data holds cannot reach the data except by a name the analysis sees, unless it
   * is a method that reads {@code this}; a call stands for a function that another function made.
   */
  private static boolean function(final NodeProgramValues.Term term) {
    final SyntaxNode expression = NodeFlowSyntax.unwrap(term.expression());
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (PROPERTY.equals(expression.kind())) {
      return !usesThis(expression);
    }
    final boolean literal = parts.size() == 1 && ANONYMOUS.equals(parts.get(0).kind());
    return literal && !usesThis(expression) || NodeProgramShapes.call(expression).isPresent();
  }

  private static boolean usesThis(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return "this".equals(NodeFlowSyntax.text(node));
    }
    return node.children().stream().anyMatch(NodeProgramUses::usesThis);
  }

  /** Whether the parameters of the callback of an element accessor are sealed. */
  boolean callbackSealed(final NodeProgramFile file, final NodeProgramShapes.Call call) {
    final Optional<NodeProgramShapes.Member> method = NodeProgramShapes.member(call.callee());
    if (method.isPresent() && "at".equals(method.get().name())) {
      return true;
    }
    if (call.arguments().isEmpty()) {
      return false;
    }
    final SyntaxNode callback =
        NodeFlowSyntax.unwrap(NodeProgramShapes.valueOf(call.arguments().get(0)));
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(callback);
    final SyntaxNode arrow =
        parts.size() == 1 && ANONYMOUS.equals(parts.get(0).kind())
            ? NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(parts.get(0)), "arrowFunctionDeclaration")
            : null;
    final SyntaxNode parameters =
        arrow == null
            ? null
            : NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(arrow), "arrowFunctionParameters");
    return parameters != null
        && file.scopes().bindings().stream()
            .filter(binding -> within(binding, parameters))
            .allMatch(binding -> seal.sealed(file, binding));
  }

  private static boolean within(final Binding binding, final SyntaxNode node) {
    return node.span().startOffset() <= binding.declaration().startOffset()
        && binding.declaration().endOffset() <= node.span().endOffset();
  }

  /** The value is an argument: of a pure function, or of a function whose parameter is sealed. */
  boolean argument(final NodeProgramFile file, final SyntaxNode argument) {
    Optional<SyntaxNode> call = file.parent(argument);
    while (call.isPresent() && !NodeProgramShapes.SINGLE.equals(call.get().kind())) {
      call = call.flatMap(file::parent);
    }
    final Optional<NodeProgramShapes.Call> shape = call.flatMap(NodeProgramShapes::call);
    if (shape.isEmpty()) {
      return false;
    }
    if (NodeProgramImports.pure(file, shape.get())) {
      return true;
    }
    final int index = shape.get().arguments().indexOf(argument);
    return index >= 0
        && NodeProgramFunctions.parameterFor(file, shape.get().callee(), index)
            .map(parameter -> seal.sealed(file, parameter))
            .orElse(false);
  }

  /** Whether the name that a declaration binds is sealed. */
  boolean declared(final NodeProgramFile file, final SyntaxNode declaration) {
    final SyntaxNode name = NodeFlowSyntax.parts(declaration).get(0);
    final SyntaxNode token =
        SHORTHAND.equals(name.kind()) ? NodeFlowSyntax.firstIdentifier(name) : null;
    return token != null
        && file.bindingOf(token).map(binding -> seal.sealed(file, binding)).orElse(false);
  }

  /** Whether the name that a loop binds to each element is sealed. */
  boolean loopBound(final NodeProgramFile file, final SyntaxNode loop) {
    final SyntaxNode bound =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(loop), "singleVariableDeclaration");
    final SyntaxNode declaration =
        bound == null ? null : NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(bound), DECLARATION);
    return declaration != null && declared(file, declaration);
  }

  /** Follows a value stored in an array or object literal to the literal's own uses. */
  boolean literal(
      final NodeProgramFile file,
      final SyntaxNode element,
      final Predicate<SyntaxNode> onward
  ) {
    Optional<SyntaxNode> node = Optional.of(element);
    for (int level = 0; level < LITERAL_LEVELS && node.isPresent(); level++) {
      node = node.flatMap(file::parent);
      if (node.isPresent() && NodeProgramShapes.SINGLE.equals(node.get().kind())) {
        return onward.test(node.get());
      }
    }
    return false;
  }
}
