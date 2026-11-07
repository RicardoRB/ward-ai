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
 * Reports a local variable of an {@code AutoCloseable} type that is given a value and then neither
 * closed nor handed on, as PMD's CloseResource does. A variable is handed on when its name is used
 * other than as the receiver of a call, for instance returned, passed or assigned; it is closed
 * when {@code name.close()} appears after it in the same block. A try-with-resources resource is
 * not a local variable declaration, so it is never reported. PMD also follows the flow into the
 * {@code finally} block; this check does not.
 */
public final class CloseResourceCheck extends FactsCheck {

  private static final String AUTO_CLOSEABLE = "java.lang.AutoCloseable";
  private static final String TYPE_TYPE = "typeType";
  private static final String DECLARATION = "localVariableDeclaration";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String BLOCK = "block";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String NULL = "=null";
  private static final String DOT = ".";
  private static final String CLOSE = "close";
  private static final FactNeed NEED =
      new FactNeed(Set.of(TYPE_TYPE), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the variable; {@code {value}} is its name
   */
  public CloseResourceCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(DECLARATION)).stream()
        .filter(visit -> isResource(visit.node(), types))
        .flatMap(visit -> leaks(visit).stream())
        .toList();
  }

  private static boolean isResource(final SyntaxNode declaration, final UnitTypeFacts types) {
    return declaration.children().stream()
        .filter(child -> TYPE_TYPE.equals(child.kind()))
        .findFirst()
        .map(type -> closeable(type, types))
        .orElse(false);
  }

  private static boolean closeable(final SyntaxNode type, final UnitTypeFacts types) {
    final Stream<String> own = types.typeOf(type.span()).map(TypeRef::name).stream();
    final Stream<String> above =
        types.membersOf(type.span()).map(TypeMembers::ancestors).stream()
            .flatMap(List::stream)
            .map(TypeRef::name);
    return Stream.concat(own, above).anyMatch(AUTO_CLOSEABLE::equals);
  }

  private List<RuleMatch> leaks(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (up.size() < 2 || !BLOCK_STATEMENT.equals(up.get(up.size() - 1).kind())) {
      return List.of();
    }
    final List<String> rest = followingTokens(up.get(up.size() - 2), up.get(up.size() - 1));
    return SyntaxWalk.descendants(visit.node()).stream()
        .filter(node -> DECLARATOR.equals(node.kind()))
        .filter(declarator -> !SyntaxWalk.text(declarator).endsWith(NULL))
        .filter(declarator -> SyntaxWalk.text(declarator).contains("="))
        .flatMap(
            declarator ->
                ClassMembers.fieldNames(declarator).stream().map(name -> pair(declarator, name)))
        .filter(named -> !closedOrHandedOn(named.name(), rest))
        .map(
            named ->
                CheckSupport.at(
                    named.declarator().span(), CheckSupport.fill(message, named.name(), "")))
        .toList();
  }

  private static List<String> followingTokens(final SyntaxNode block, final SyntaxNode statement) {
    if (!BLOCK.equals(block.kind())) {
      return List.of();
    }
    final List<SyntaxNode> siblings = block.children();
    return siblings.subList(siblings.indexOf(statement) + 1, siblings.size()).stream()
        .flatMap(next -> SyntaxWalk.leaves(next).stream())
        .map(SyntaxWalk::text)
        .toList();
  }

  /** Tells whether the tokens close the variable, or use its name other than as a call receiver. */
  private static boolean closedOrHandedOn(final String name, final List<String> tokens) {
    for (int index = 0; index < tokens.size(); index++) {
      if (!name.equals(tokens.get(index)) || index > 0 && DOT.equals(tokens.get(index - 1))) {
        continue;
      }
      final boolean receiver = index + 1 < tokens.size() && DOT.equals(tokens.get(index + 1));
      if (!receiver || index + 2 < tokens.size() && CLOSE.equals(tokens.get(index + 2))) {
        return true;
      }
    }
    return false;
  }

  private static Named pair(final SyntaxNode declarator, final String name) {
    return new Named(declarator, name);
  }

  private record Named(SyntaxNode declarator, String name) {}
}
