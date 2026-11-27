package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Follows an expression to the expressions its value can be, across names, parameters, loops and
 * files. A name that holds an array or an object is followed only when no code can change it (see
 * {@link NodeProgramSeal}); anything the analysis cannot follow gives no answer.
 *
 * <p>shortcut: a call is followed only for the element accessors of an array and for the one
 * function that only rewrites its argument (see {@link NodeProgramImports}); upgrade when a project
 * builds its constant data with other helpers.
 */
final class NodeProgramValues {
  private static final String PROPERTY = "propertyAssignment";
  private static final int MAX_DEPTH = 24;
  private static final int BINARY_PARTS = 3;
  private static final int UNARY_PARTS = 2;
  private static final int TERNARY_PARTS = 5;
  private static final int RIGHT = 2;
  private static final int BRANCH_ELSE = 4;

  private final NodeProgramFiles files;
  private final NodeProgramBudget budget;
  private final NodeProgramElements access = new NodeProgramElements(this);
  private final NodeProgramOperators operators = new NodeProgramOperators(this, access);
  private final Set<String> active = new HashSet<>();
  private NodeProgramSeal seal;
  private int reentries;

  /** An expression a value can be, with the file it is written in. */
  record Term(NodeProgramFile file, SyntaxNode expression) {}

  NodeProgramValues(final NodeProgramFiles files) {
    this(files, new NodeProgramBudget());
  }

  private NodeProgramValues(final NodeProgramFiles files, final NodeProgramBudget budget) {
    this.files = files;
    this.budget = budget;
  }

  NodeProgramFiles files() {
    return files;
  }

  NodeProgramSeal seal() {
    return seal;
  }

  NodeProgramElements access() {
    return access;
  }

  /**
   * How many times a name was reached again while its own values were being followed. A caller
   * that needs the complete values checks that it did not change.
   */
  int reentries() {
    return reentries;
  }

  /** Counts one step of the analysis against a budget that bounds the work for one sink. */
  boolean spend() {
    return budget.spend();
  }

  /** A follower with no name in progress, for a question asked in the middle of another. */
  NodeProgramValues scratch() {
    final NodeProgramValues fresh = new NodeProgramValues(files, budget);
    fresh.seal = seal;
    return fresh;
  }

  void sealWith(final NodeProgramSeal sealing) {
    this.seal = sealing;
  }

  /** The expressions that the value of {@code node} can be, or none when it is not known. */
  Optional<List<Term>> of(final NodeProgramFile file, final SyntaxNode node, final int depth) {
    final SyntaxNode expression = NodeFlowSyntax.unwrap(node);
    if (depth > MAX_DEPTH || !spend()) {
      return Optional.empty();
    }
    if (PROPERTY.equals(expression.kind())) {
      return access.property(file, expression, depth);
    }
    if (!NodeProgramShapes.SINGLE.equals(expression.kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    return switch (parts.size()) {
      case 1 -> single(file, expression, parts.get(0), depth);
      case UNARY_PARTS -> operators.twoParts(file, expression, parts, depth);
      case BINARY_PARTS -> operators.threeParts(file, expression, parts, depth);
      case TERNARY_PARTS -> access.union(file, depth, parts.get(RIGHT), parts.get(BRANCH_ELSE));
      default -> access.index(file, expression, depth);
    };
  }

  private Optional<List<Term>> single(
      final NodeProgramFile file,
      final SyntaxNode expression,
      final SyntaxNode part,
      final int depth
  ) {
    return switch (part.kind()) {
      case "literal", "arrayLiteral", "objectLiteral", "anonymousFunction" ->
          Optional.of(List.of(new Term(file, expression)));
      case "identifierName" ->
          NodeProgramShapes.name(expression).flatMap(token -> resolve(file, token, depth));
      default -> Optional.empty();
    };
  }

  /** The values of the name at {@code token}, when what it holds can be followed. */
  Optional<List<Term>> resolve(
      final NodeProgramFile file,
      final SyntaxNode token,
      final int depth
  ) {
    final Optional<Binding> binding = Optional.ofNullable(token).flatMap(file::bindingOf);
    if (binding.isEmpty()
        || file.open()
        || binding.get().has(BindingFlag.TYPE_ONLY)
        || !seal.sealed(file, binding.get())) {
      return Optional.empty();
    }
    final String key = file.path() + "@" + binding.get().declaration().startOffset();
    if (!active.add(key)) {
      reentries++;
      return Optional.of(List.of());
    }
    try {
      return switch (binding.get().kind()) {
        case VARIABLE, CONSTANT -> NodeProgramBindings.variable(this, file, binding.get(), depth);
        case PARAMETER -> NodeProgramBindings.parameter(this, file, binding.get(), depth);
        case IMPORT -> NodeProgramImports.imported(this, file, binding.get(), depth);
        default -> Optional.empty();
      };
    } finally {
      active.remove(key);
    }
  }
}
