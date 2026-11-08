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
 * Reports a lifecycle method that does not call its {@code super} method first, or last, as PMD's
 * CallSuperFirst and CallSuperLast do. The class must be, or extend, one of the named types; the
 * method must be one of the named methods; and the first, or last, statement of its body must be
 * {@code super.name(...)}. A class whose supertypes are unknown is not reported.
 */
public final class CallSuperCheck extends FactsCheck {

  /** Where the call to the super method must be. */
  public enum Position {
    /** The first statement of the method. */
    FIRST,
    /** The last statement of the method. */
    LAST
  }

  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final FactNeed NEED =
      new FactNeed(Set.of(MemberDeclarations.CLASS), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final Position position;
  private final Set<String> methods;
  private final Set<String> ancestors;
  private final String message;

  /**
   * Creates the check.
   *
   * @param position where the call must be
   * @param methods the names of the methods that must call their super method
   * @param ancestors the qualified names of the types a class must be or extend
   * @param message the message reported at the method; {@code {value}} is its name
   */
  public CallSuperCheck(
      final Position position,
      final Set<String> methods,
      final Set<String> ancestors,
      final String message) {
    super(NEED);
    this.position = Objects.requireNonNull(position, "position");
    this.methods = Set.copyOf(methods);
    this.ancestors = Set.copyOf(ancestors);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(clazz -> isLifecycleClass(clazz, types))
        .flatMap(clazz -> ClassMembers.members(clazz).stream())
        .map(ClassMembers::declarationOf)
        .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
        .filter(declaration -> methods.contains(MemberDeclarations.name(declaration)))
        .filter(declaration -> !callsSuper(declaration))
        .map(declaration -> CheckSupport.at(declaration.span(), filled(declaration)))
        .toList();
  }

  private String filled(final SyntaxNode method) {
    return CheckSupport.fill(message, MemberDeclarations.name(method), "");
  }

  private boolean isLifecycleClass(final SyntaxNode clazz, final UnitTypeFacts types) {
    final Stream<String> own = types.typeOf(clazz.span()).map(TypeRef::name).stream();
    final Stream<String> above =
        types.membersOf(clazz.span()).map(TypeMembers::ancestors).stream()
            .flatMap(List::stream)
            .map(TypeRef::name);
    return Stream.concat(own, above).anyMatch(ancestors::contains);
  }

  private boolean callsSuper(final SyntaxNode method) {
    final List<SyntaxNode> statements =
        SyntaxWalk.firstDescendant(method, BLOCK, Set.of()).stream()
            .flatMap(block -> block.children().stream())
            .filter(child -> BLOCK_STATEMENT.equals(child.kind()))
            .toList();
    if (statements.isEmpty()) {
      return false;
    }
    final SyntaxNode wanted =
        position == Position.FIRST ? statements.get(0) : statements.get(statements.size() - 1);
    final String text = SyntaxWalk.text(wanted);
    return text.startsWith("super." + MemberDeclarations.name(method) + "(") && text.endsWith(";");
  }
}
