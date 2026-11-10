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
  private final Set<String> resourceTypes;
  private final Set<String> ignored;
  private final boolean createdHere;
  private final boolean exceptionPath;

  /**
   * Creates the check for any {@code AutoCloseable} that is never closed.
   *
   * @param message the message reported at the variable; {@code {value}} is its name
   */
  public CloseResourceCheck(final String message) {
    this(message, Set.of(AUTO_CLOSEABLE), Set.of(), false, false);
  }

  /**
   * Creates the check.
   *
   * @param message the message reported at the variable; {@code {value}} is its name
   * @param types the types, as the type or one of its ancestors, that make a variable a resource
   * @param ignored the exact types that are never reported
   * @param createdHere whether only a value made by {@code new} counts
   * @param exceptionPath whether to report a resource closed only on the normal path
   */
  public CloseResourceCheck(
      final String message,
      final Set<String> types,
      final Set<String> ignored,
      final boolean createdHere,
      final boolean exceptionPath) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
    this.resourceTypes = Set.copyOf(types);
    this.ignored = Set.copyOf(ignored);
    this.createdHere = createdHere;
    this.exceptionPath = exceptionPath;
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(DECLARATION)).stream()
        .filter(visit -> isResource(visit.node(), types))
        .flatMap(visit -> leaks(visit).stream())
        .toList();
  }

  private boolean isResource(final SyntaxNode declaration, final UnitTypeFacts types) {
    return declaration.children().stream()
        .filter(child -> TYPE_TYPE.equals(child.kind()))
        .findFirst()
        .map(type -> closeable(type, types))
        .orElse(false);
  }

  private boolean closeable(final SyntaxNode type, final UnitTypeFacts facts) {
    final Stream<String> own = facts.typeOf(type.span()).map(TypeRef::name).stream();
    final Stream<String> above =
        facts.membersOf(type.span()).map(TypeMembers::ancestors).stream()
            .flatMap(List::stream)
            .map(TypeRef::name);
    final List<String> names = Stream.concat(own, above).toList();
    return names.stream().anyMatch(resourceTypes::contains)
        && facts.typeOf(type.span()).map(TypeRef::name).filter(ignored::contains).isEmpty();
  }

  private List<RuleMatch> leaks(final SyntaxWalk.Visit visit) {
    return exceptionPath ? exceptionLeaks(visit) : neverClosed(visit);
  }

  private List<RuleMatch> neverClosed(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (up.size() < 2 || !BLOCK_STATEMENT.equals(up.get(up.size() - 1).kind())) {
      return List.of();
    }
    final List<String> rest = followingTokens(up.get(up.size() - 2), up.get(up.size() - 1));
    return SyntaxWalk.descendants(visit.node()).stream()
        .filter(node -> DECLARATOR.equals(node.kind()))
        .filter(declarator -> !SyntaxWalk.text(declarator).endsWith(NULL))
        .filter(declarator -> SyntaxWalk.text(declarator).contains("="))
        .filter(this::madeHere)
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

  private boolean madeHere(final SyntaxNode declarator) {
    return !createdHere || SyntaxWalk.text(declarator).contains("=new");
  }

  /** Reports a resource whose only close is a statement of the same block after other calls. */
  private List<RuleMatch> exceptionLeaks(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (up.size() < 2
        || !BLOCK_STATEMENT.equals(up.get(up.size() - 1).kind())
        || !BLOCK.equals(up.get(up.size() - 2).kind())) {
      return List.of();
    }
    final List<SyntaxNode> siblings = up.get(up.size() - 2).children();
    final List<SyntaxNode> rest =
        siblings.subList(siblings.indexOf(up.get(up.size() - 1)) + 1, siblings.size());
    return SyntaxWalk.descendants(visit.node()).stream()
        .filter(node -> DECLARATOR.equals(node.kind()))
        .filter(this::madeHere)
        .flatMap(
            declarator ->
                ClassMembers.fieldNames(declarator).stream().map(name -> pair(declarator, name)))
        .filter(named -> CloseAfterCalls.closedAfterCalls(named.name(), rest))
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
  static boolean closedOrHandedOn(final String name, final List<String> tokens) {
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
