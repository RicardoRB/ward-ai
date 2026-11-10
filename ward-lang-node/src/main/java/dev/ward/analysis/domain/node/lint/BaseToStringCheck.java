package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reports an object converted to a string, in a template placeholder, by {@code String(value)} or
 * by {@code value.toString()}, when its type neither declares nor inherits its own {@code
 * toString}, so it prints as {@code [object Object]}.
 */
public final class BaseToStringCheck extends TypedCheck {

  private static final String TEMPLATE_OPEN = "${";
  private static final String TO_STRING = "toString";
  private static final String STRING_CALL = "String";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String ARGUMENT = "argument";
  private static final String DOT = ".";

  private final String message;

  /** Creates the check with the message of the rule. */
  public BaseToStringCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected Set<FactKind> kinds() {
    return Set.of(FactKind.TYPE, FactKind.MEMBERS);
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode atom : SyntaxText.nodesOf(root, TEMPLATE)) {
      final List<SyntaxNode> parts = atom.children();
      if (parts.size() == SyntaxText.INFIX_PARTS
          && SyntaxText.isToken(parts.get(0), TEMPLATE_OPEN)) {
        reportIfBase(types, parts.get(1), out);
      }
    }
    for (final SyntaxNode call : SyntaxText.nodesOf(root, EXPRESSION)) {
      stringConversion(call, types, out);
    }
    return out;
  }

  /** Reports {@code String(value)} and {@code value.toString()} of a value without a toString. */
  private void stringConversion(
      final SyntaxNode call, final UnitTypeFacts types, final List<RuleMatch> out) {
    final List<SyntaxNode> parts = call.children();
    if (parts.size() != 2 || !ARGUMENTS.equals(parts.get(1).kind())) {
      return;
    }
    final SyntaxNode callee = parts.get(0);
    if (STRING_CALL.equals(SyntaxText.of(callee))) {
      firstArgument(parts.get(1)).ifPresent(value -> reportIfBase(types, value, out));
    } else if (isToStringMember(callee)) {
      reportIfBase(types, callee.children().get(0), out);
    }
  }

  /** Reports whether a callee is {@code <object>.toString}. */
  private static boolean isToStringMember(final SyntaxNode callee) {
    final List<SyntaxNode> parts = callee.children();
    return parts.size() == SyntaxText.INFIX_PARTS
        && EXPRESSION.equals(parts.get(0).kind())
        && SyntaxText.isToken(parts.get(1), DOT)
        && TO_STRING.equals(SyntaxText.of(parts.get(2)));
  }

  /**
   * Returns the first argument expression of an argument list, if any. TypeScript wraps the
   * arguments in an {@code argumentList}; JavaScript lists each {@code argument} directly.
   */
  private static Optional<SyntaxNode> firstArgument(final SyntaxNode arguments) {
    final List<SyntaxNode> direct = SyntaxText.childrenOf(arguments, ARGUMENT);
    final List<SyntaxNode> wrapped =
        SyntaxText.childrenOf(arguments, ARGUMENT_LIST).stream()
            .flatMap(list -> SyntaxText.childrenOf(list, ARGUMENT).stream())
            .toList();
    return Stream.concat(direct.stream(), wrapped.stream())
        .map(argument -> SyntaxText.childrenOf(argument, EXPRESSION))
        .filter(values -> !values.isEmpty())
        .map(values -> values.get(0))
        .findFirst();
  }

  private void reportIfBase(
      final UnitTypeFacts types, final SyntaxNode value, final List<RuleMatch> out) {
    if (isBase(types, value)) {
      out.add(new RuleMatch(value.span(), message));
    }
  }

  /** Reports an object type whose members are known and have no {@code toString}. */
  private static boolean isBase(final UnitTypeFacts types, final SyntaxNode value) {
    final Optional<TypeRef> type = typeOf(types, value);
    final Optional<TypeMembers> members = types.membersOf(value.span());
    return type.isPresent()
        && ValueKinds.of(type.get()) == ValueKind.OBJECT
        && members.isPresent()
        && !declaresToString(members.get());
  }

  private static boolean declaresToString(final TypeMembers members) {
    return hasToString(members.methods()) || hasToString(members.inheritedMethods());
  }

  private static boolean hasToString(final List<CallTarget> methods) {
    return methods.stream().anyMatch(method -> TO_STRING.equals(method.name()));
  }
}
