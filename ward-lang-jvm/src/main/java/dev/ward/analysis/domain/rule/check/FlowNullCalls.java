package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads a call or an object creation of this file: its name and its argument expressions. */
final class FlowNullCalls {

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final int QUALIFIED = 3;

  private FlowNullCalls() {}

  /** A call: its name, arguments, and whether it has a receiver other than {@code this}. */
  record Call(String name, List<SyntaxNode> args, boolean foreign) {}

  /** Returns the call or object creation that {@code expression} is, if it is one. */
  static Optional<Call> of(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    if (!EXPRESSION.equals(expression.kind()) || parts.isEmpty()) {
      return Optional.empty();
    }
    if (parts.size() == 1 && METHOD_CALL.equals(parts.get(0).kind())) {
      return Optional.of(call(parts.get(0), false));
    }
    if (parts.size() == QUALIFIED && METHOD_CALL.equals(parts.get(2).kind())) {
      return Optional.of(call(parts.get(2), !"this".equals(SyntaxWalk.text(parts.get(0)))));
    }
    if (parts.size() == 2 && "new".equals(SyntaxWalk.text(parts.get(0)))) {
      return Optional.of(creation(parts.get(1)));
    }
    return Optional.empty();
  }

  private static Call call(final SyntaxNode methodCall, final boolean foreign) {
    final String name =
        methodCall.children().stream()
            .filter(child -> "identifier".equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    return new Call(name, arguments(methodCall), foreign);
  }

  private static Call creation(final SyntaxNode creator) {
    final String text =
        creator.children().stream()
            .filter(child -> "createdName".equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    final int generic = text.indexOf('<');
    return new Call(generic < 0 ? text : text.substring(0, generic), arguments(creator), false);
  }

  /** Returns the argument expressions below {@code holder}. */
  static List<SyntaxNode> arguments(final SyntaxNode holder) {
    return SyntaxWalk.firstDescendant(holder, "arguments", Set.of("classBody"))
        .flatMap(
            args ->
                args.children().stream().filter(c -> "expressionList".equals(c.kind())).findFirst())
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
        .orElse(List.of());
  }
}
