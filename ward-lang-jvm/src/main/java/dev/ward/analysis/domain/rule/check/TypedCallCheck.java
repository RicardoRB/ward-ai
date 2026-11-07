package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call {@code receiver.method(arguments)} whose method name matches a pattern and whose
 * receiver, one argument, or both fit what the rule describes: the receiver by its text or type,
 * the argument by its type or text. A condition that is not set does not limit. It reports nothing
 * when a type it needs is unknown.
 */
public final class TypedCallCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final Pattern method;
  private final Receiver receiver;
  private final Argument argument;
  private final String message;

  /**
   * What the receiver must match; a part that is empty does not limit.
   *
   * @param text matches the source text of the receiver
   * @param type matches the name of the static type of the receiver
   */
  public record Receiver(Optional<Pattern> text, Optional<Pattern> type) {

    /** The receiver that does not limit the call. */
    public static final Receiver ANY = new Receiver(Optional.empty(), Optional.empty());

    /**
     * Checks the parts.
     *
     * @throws NullPointerException if a part is missing
     */
    public Receiver {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(type, "type");
    }
  }

  /**
   * What one argument must match; a part that is empty does not limit.
   *
   * @param index the index of the argument
   * @param types the allowed type names of the argument; empty allows any
   * @param text matches the source text of the argument
   */
  public record Argument(int index, Set<String> types, Optional<Pattern> text) {

    /** The argument that does not limit the call. */
    public static final Argument ANY = new Argument(0, Set.of(), Optional.empty());

    /**
     * Copies the types.
     *
     * @throws NullPointerException if a part is missing
     */
    public Argument {
      types = Set.copyOf(types);
      Objects.requireNonNull(text, "text");
    }

    boolean limits() {
      return !types.isEmpty() || text.isPresent();
    }
  }

  /**
   * Creates the check.
   *
   * @param method matches the name of the called method
   * @param receiver what the receiver must match
   * @param argument what one argument must match
   * @param message the message reported at the call
   */
  public TypedCallCheck(
      final Pattern method,
      final Receiver receiver,
      final Argument argument,
      final String message) {
    this.method = Objects.requireNonNull(method, "method");
    this.receiver = Objects.requireNonNull(receiver, "receiver");
    this.argument = Objects.requireNonNull(argument, "argument");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts facts,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> reported(call, facts))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private boolean reported(final SyntaxNode call, final UnitTypeFacts facts) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> invoked =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || invoked.isEmpty()) {
      return false;
    }
    final SyntaxNode target = parts.get(0);
    return nameMatches(invoked.get())
        && receiver.text().map(p -> p.matcher(SyntaxWalk.text(target)).matches()).orElse(true)
        && receiver.type().map(p -> typeMatches(target, p, facts)).orElse(true)
        && argumentFits(arguments(invoked.get()), facts);
  }

  private boolean nameMatches(final SyntaxNode invoked) {
    return invoked.children().stream()
        .filter(child -> "identifier".equals(child.kind()))
        .anyMatch(child -> method.matcher(SyntaxWalk.text(child)).matches());
  }

  private static boolean typeMatches(
      final SyntaxNode node, final Pattern pattern, final UnitTypeFacts facts) {
    return facts
        .typeOf(node.span())
        .map(TypeRef::name)
        .filter(n -> pattern.matcher(n).matches())
        .isPresent();
  }

  private boolean argumentFits(final List<SyntaxNode> arguments, final UnitTypeFacts facts) {
    if (!argument.limits()) {
      return true;
    }
    if (argument.index() >= arguments.size()) {
      return false;
    }
    final SyntaxNode node = arguments.get(argument.index());
    final boolean typed =
        argument.types().isEmpty()
            || facts
                .typeOf(node.span())
                .map(TypeRef::name)
                .filter(argument.types()::contains)
                .isPresent();
    return typed
        && argument.text().map(p -> p.matcher(SyntaxWalk.text(node)).matches()).orElse(true);
  }

  private static List<SyntaxNode> arguments(final SyntaxNode invoked) {
    return SyntaxWalk.descendants(invoked).stream()
        .filter(node -> "expressionList".equals(node.kind()))
        .findFirst()
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
        .orElse(List.of());
  }
}
