package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Optional;

/**
 * Types the expressions of one Python file from literals, annotations, initializers, source
 * definitions and stubs. Anything it cannot determine is unknown, never a guess.
 */
final class PythonExpressionTypes {

  private static final int MAX_DEPTH = 8;
  private static final int SINGLE_PART = 1;
  private static final int PAIR_PARTS = 2;
  private static final String AWAIT = "await";
  private static final String ATTRIBUTE = ".";
  private static final String CALL_OPEN = "(";
  private static final String TUPLE_OPEN = "(";
  private static final String COMMA = ",";
  private static final String TUPLE = "tuple";

  private final PythonNameTypes names;
  private final PythonMembers members;

  /** Creates the typer over the indexed file and the stubs it may consult. */
  PythonExpressionTypes(final PythonTypeIndex index, final PythonStubs stubs) {
    final PythonSignatures signatures = new PythonSignatures(stubs);
    this.members = new PythonMembers(index, stubs, signatures);
    this.names = new PythonNameTypes(index, stubs, members);
  }

  /** Returns the type of {@code expression}, or empty when it is not known. */
  Optional<TypeRef> typeOf(final SyntaxNode expression) {
    return Optional.ofNullable(evaluate(expression, 0).type());
  }

  /** Returns the target of the call that {@code expression} is, when it is a call of a stub. */
  Optional<CallTarget> callOf(final SyntaxNode expression) {
    return Optional.ofNullable(evaluate(expression, 0).call());
  }

  private PythonSubject evaluate(final SyntaxNode raw, final int depth) {
    if (depth > MAX_DEPTH) {
      return PythonSubject.UNKNOWN;
    }
    final SyntaxNode node = PythonTypeKinds.descend(raw);
    if (PythonTypeKinds.ATOM_EXPR.equals(node.kind())) {
      return chain(node, depth);
    }
    if (PythonTypeKinds.ATOM.equals(node.kind())) {
      return atom(node, depth);
    }
    if (PythonTypeKinds.isName(node)) {
      return names.subjectOf(node, initializer -> typeAt(initializer, depth + 1));
    }
    return PythonLiterals.of(node).map(PythonSubject::value).orElse(PythonSubject.UNKNOWN);
  }

  private Optional<TypeRef> typeAt(final SyntaxNode expression, final int depth) {
    return Optional.ofNullable(evaluate(expression, depth).type());
  }

  private PythonSubject atom(final SyntaxNode node, final int depth) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == SINGLE_PART) {
      return evaluate(parts.get(0), depth + 1);
    }
    if (parts.stream().allMatch(part -> PythonTypeKinds.STRING.equals(part.kind()))) {
      return PythonLiterals.of(parts.get(0))
          .map(PythonSubject::value)
          .orElse(PythonSubject.UNKNOWN);
    }
    if (TUPLE_OPEN.equals(PythonSyntax.text(parts.get(0)))) {
      return parenthesized(parts, depth);
    }
    return PythonLiterals.collectionName(parts)
        .map(names::builtinValue)
        .orElse(PythonSubject.UNKNOWN);
  }

  private PythonSubject parenthesized(final List<SyntaxNode> parts, final int depth) {
    if (parts.size() == PAIR_PARTS) {
      return names.builtinValue(TUPLE);
    }
    final SyntaxNode inner = parts.get(1);
    if (!PythonTypeKinds.TESTLIST_COMP.equals(inner.kind())) {
      return PythonSubject.UNKNOWN;
    }
    final List<SyntaxNode> items = inner.children();
    if (items.stream().anyMatch(item -> PythonTypeKinds.COMP_FOR.equals(item.kind()))) {
      return PythonSubject.UNKNOWN;
    }
    if (items.stream().anyMatch(item -> COMMA.equals(PythonSyntax.text(item)))) {
      return names.builtinValue(TUPLE);
    }
    return items.isEmpty() ? PythonSubject.UNKNOWN : evaluate(items.get(0), depth + 1);
  }

  private PythonSubject chain(final SyntaxNode node, final int depth) {
    final List<SyntaxNode> parts = node.children();
    if (AWAIT.equals(PythonSyntax.text(parts.get(0)))) {
      return PythonSubject.UNKNOWN;
    }
    PythonSubject current = evaluate(parts.get(0), depth + 1);
    for (final SyntaxNode trailer : parts.subList(1, parts.size())) {
      current = apply(current, trailer);
    }
    return current;
  }

  private PythonSubject apply(final PythonSubject current, final SyntaxNode trailer) {
    final String symbol = PythonSyntax.text(trailer.children().get(0));
    if (ATTRIBUTE.equals(symbol)) {
      return members.attribute(current, PythonNames.identifier(trailer.children().get(1)));
    }
    return CALL_OPEN.equals(symbol) ? invoke(current) : PythonSubject.UNKNOWN;
  }

  private PythonSubject invoke(final PythonSubject current) {
    return switch (current.kind()) {
      case STUB_FUNCTION -> members.invokeStub(current);
      case CLASS -> members.instance(current.name());
      case SOURCE_FUNCTION -> sourceReturn(current.definition());
      default -> PythonSubject.UNKNOWN;
    };
  }

  private PythonSubject sourceReturn(final SyntaxNode definition) {
    return PythonSignatures.returnAnnotation(definition)
        .flatMap(names::annotationType)
        .map(PythonSubject::value)
        .orElse(PythonSubject.UNKNOWN);
  }
}
