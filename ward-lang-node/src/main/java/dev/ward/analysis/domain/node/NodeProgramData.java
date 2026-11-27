package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RelatedFile;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Decides whether an expression only holds data that the program itself wrote: string and number
 * constants, and the strings reached through names, parameters and loops from arrays and objects
 * of such constants, in the file or in files it imports. Request data cannot be among them, because
 * a name that holds an array or an object counts only when nothing can change it (see {@link
 * NodeProgramSeal}), and a parameter counts only when every call of its function is known and
 * passes such data (see {@link NodeProgramFunctions}).
 */
final class NodeProgramData {
  private static final String PROPERTY = "propertyAssignment";
  private static final String PLUS = "+";
  private static final int MAX_DEPTH = 24;
  private static final int BINARY_PARTS = 3;
  private static final int RIGHT = 2;

  private final NodeProgramFiles files;
  private final NodeProgramValues values;
  private final Predicate<SyntaxNode> fallback;

  /**
   * Creates the analysis over a file and the files it depends on.
   *
   * @param unit the file with the sink
   * @param root its syntax tree
   * @param related the files it depends on, with their trees
   * @param fallback decides the expressions of the file that the analysis does not follow
   */
  NodeProgramData(
      final SourceUnit unit,
      final SyntaxNode root,
      final List<RelatedFile> related,
      final Predicate<SyntaxNode> fallback
  ) {
    this.files = new NodeProgramFiles(unit, root, related);
    this.values = new NodeProgramValues(files);
    values.sealWith(new NodeProgramSeal(values));
    this.fallback = fallback;
  }

  /** Whether the expression, written in the file with the sink, only holds program data. */
  boolean holds(final SyntaxNode expression) {
    return holds(files.own(), expression, 0);
  }

  private boolean holds(final NodeProgramFile file, final SyntaxNode expression, final int depth) {
    if (depth > MAX_DEPTH) {
      return false;
    }
    final Optional<List<NodeProgramValues.Term>> terms = values.of(file, expression, depth);
    if (terms.isPresent() && terms.get().stream().allMatch(term -> deep(term, depth + 1))) {
      return true;
    }
    return file == files.own() && fallback.test(expression);
  }

  /** Whether the parts of the value that a term stands for are program data too. */
  private boolean deep(final NodeProgramValues.Term term, final int depth) {
    final SyntaxNode expression = term.expression();
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (PROPERTY.equals(expression.kind())) {
      return true;
    }
    return switch (parts.size()) {
      case 1 -> literal(term, parts.get(0), depth);
      case BINARY_PARTS -> binary(term, parts, depth);
      default -> call(term, depth);
    };
  }

  private boolean literal(
      final NodeProgramValues.Term term,
      final SyntaxNode part,
      final int depth
  ) {
    final NodeProgramFile file = term.file();
    return switch (part.kind()) {
      case "literal" -> NodeTemplates.of(term.expression())
          .map(template -> holes(file, template, depth))
          .orElse(true);
      case "arrayLiteral" -> NodeProgramShapes.elements(part)
          .map(items -> items.stream().allMatch(item -> holds(file, item, depth)))
          .orElse(false);
      case "objectLiteral" -> NodeProgramObjects.values(term)
          .map(items -> items.stream().allMatch(item -> holds(file, item, depth)))
          .orElse(false);
      case "anonymousFunction" -> true;
      default -> false;
    };
  }

  private boolean holes(final NodeProgramFile file, final SyntaxNode template, final int depth) {
    return NodeFlowSyntax.parts(template).stream()
        .map(NodeTemplates::expression)
        .flatMap(Optional::stream)
        .allMatch(expression -> holds(file, expression, depth));
  }

  private boolean binary(
      final NodeProgramValues.Term term,
      final List<SyntaxNode> parts,
      final int depth
  ) {
    final String operator = NodeFlowSyntax.text(parts.get(1));
    if (PLUS.equals(operator)) {
      return holds(term.file(), parts.get(0), depth) && holds(term.file(), parts.get(RIGHT), depth);
    }
    return NodeNumericShapes.operator(operator)
        || NodeProgramShapes.member(term.expression())
            .filter(member -> NodeNumericShapes.member(member.name()))
            .isPresent();
  }

  private boolean call(final NodeProgramValues.Term term, final int depth) {
    final Optional<NodeProgramShapes.Call> call = NodeProgramShapes.call(term.expression());
    if (call.isPresent()) {
      return NodeProgramImports.pure(term.file(), call.get())
          && call.get().arguments().stream()
              .allMatch(argument -> holds(term.file(), NodeProgramShapes.valueOf(argument), depth));
    }
    return NodeNumericShapes.unaryOrCall(NodeFlowSyntax.parts(term.expression()));
  }
}
