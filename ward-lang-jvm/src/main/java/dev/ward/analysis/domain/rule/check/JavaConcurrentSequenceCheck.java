package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code put} on a {@code ConcurrentHashMap} inside the branch of an {@code if} that
 * tests the map with {@code containsKey} or {@code get} alone, as SpotBugs'
 * AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION does: the two calls are not atomic together. The
 * map is known by its declared type, so a {@code ConcurrentMap} variable is not reported.
 */
public final class JavaConcurrentSequenceCheck implements RuleCheck {

  private static final String MAP = "ConcurrentHashMap";
  private static final String QUALIFIED = "java.util.concurrent.ConcurrentHashMap";
  private static final int CONDITION = 2;
  private static final int BRANCH = 4;
  private static final int CALL_PARTS = 3;
  private static final Set<String> DECLARATIONS =
      Set.of("fieldDeclaration", "localVariableDeclaration", "formalParameter");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public JavaConcurrentSequenceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> maps = maps(root);
    final List<RuleMatch> matches = new ArrayList<>();
    if (!maps.isEmpty()) {
      final Pattern test = test(maps);
      for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
        branchPut(visit.node(), test, maps)
            .ifPresent(put -> matches.add(CheckSupport.at(put.span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<SyntaxNode> branchPut(
      final SyntaxNode statement, final Pattern test, final Set<String> maps) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.size() <= BRANCH
        || !"'if'".equals(parts.get(0).kind())
        || !test.matcher(SyntaxWalk.text(parts.get(CONDITION))).matches()) {
      return Optional.empty();
    }
    return SyntaxWalk.descendants(parts.get(BRANCH)).stream()
        .filter(node -> isPutOn(node, maps))
        .map(node -> node.children().get(2))
        .findFirst();
  }

  private static boolean isPutOn(final SyntaxNode node, final Set<String> maps) {
    final List<SyntaxNode> parts = node.children();
    return "expression".equals(node.kind())
        && parts.size() == CALL_PARTS
        && "'.'".equals(parts.get(1).kind())
        && "methodCall".equals(parts.get(2).kind())
        && JavaNodes.kid(parts.get(2), "identifier")
            .map(SyntaxWalk::text)
            .filter("put"::equals)
            .isPresent()
        && maps.contains(SyntaxWalk.text(parts.get(0)).replaceFirst("^this\\.", ""));
  }

  private static Pattern test(final Set<String> maps) {
    final String map =
        "(?:this\\.)?(?:" + String.join("|", maps.stream().map(Pattern::quote).toList()) + ")";
    return Pattern.compile(
        "^!?"
            + map
            + "\\.containsKey\\(.*\\)$|^"
            + map
            + "\\.get\\(.*\\)(?:==|!=)null$|^null(?:==|!=)"
            + map
            + "\\.get\\(.*\\)$");
  }

  private static Set<String> maps(final SyntaxNode root) {
    final Set<String> imports = JavaAnnotations.imports(root);
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (DECLARATIONS.contains(node.kind()) && concurrentHashMap(node, imports)) {
        SyntaxWalk.descendants(node).stream()
            .filter(id -> "variableDeclaratorId".equals(id.kind()))
            .map(SyntaxWalk::text)
            .forEach(names::add);
      }
    }
    return names;
  }

  private static boolean concurrentHashMap(final SyntaxNode node, final Set<String> imports) {
    return ClassMembers.typeText(node)
        .map(type -> type.replaceAll("<.*>", ""))
        .filter(
            type ->
                QUALIFIED.equals(type)
                    || MAP.equals(type) && JavaAnnotations.resolves(imports, MAP, QUALIFIED))
        .isPresent();
  }
}
