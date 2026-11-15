package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reports a call of {@code close()} in a {@code finally} block whose receiver is an {@code
 * AutoCloseable}, as PMD's UseTryWithResources does: a receiver whose type is not known to be
 * closeable, such as an {@code XMLStreamReader}, cannot be a try-with-resources resource.
 */
public final class CloseInFinallyCheck extends FactsCheck {
  private static final String EXPRESSION = "expression";
  private static final String AUTO_CLOSEABLE = "java.lang.AutoCloseable";
  private static final String CLOSE_CALL = ".close()";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public CloseInFinallyCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of("finallyBlock")).stream()
        .flatMap(visit -> SyntaxWalk.descendants(visit.node()).stream())
        .filter(node -> EXPRESSION.equals(node.kind()))
        .filter(node -> SyntaxWalk.text(node).endsWith(CLOSE_CALL))
        .filter(node -> closeable(node, types))
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private static boolean closeable(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind())) {
      return false;
    }
    final SyntaxNode receiver = parts.get(0);
    final Stream<String> own = types.typeOf(receiver.span()).map(TypeRef::name).stream();
    final Stream<String> above =
        types.membersOf(receiver.span()).map(TypeMembers::ancestors).stream()
            .flatMap(List::stream)
            .map(TypeRef::name);
    return Stream.concat(own, above).anyMatch(AUTO_CLOSEABLE::equals);
  }
}
