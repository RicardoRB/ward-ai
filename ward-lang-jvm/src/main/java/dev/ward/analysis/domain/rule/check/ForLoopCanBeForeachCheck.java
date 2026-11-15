package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a counting loop over a collection or array whose index only reads elements, as {@code
 * list.get(i)} or {@code array[i]}. The index must start at zero, count up to the size or length of
 * the same collection, and appear nowhere else in the body. An element that is assigned is not only
 * read. A collection read with {@code get} must be known to be {@code Iterable}; an array needs no
 * type.
 */
public final class ForLoopCanBeForeachCheck implements RuleCheck {

  private static final Set<String> ITERABLES =
      Set.of("java.lang.Iterable", "java.util.Collection", "java.util.List");
  private static final FactNeed NEED =
      new FactNeed(Set.of("expression"), Set.of(FactKind.TYPE, FactKind.MEMBERS));
  private static final String ASSIGNED = "(?!(?:[-+*/%&|^]|<<|>>>?)?=(?!=)|\\+\\+|--)";

  private static final int CONTROL_PARTS = 3;
  private static final int BOUND_KIND = 3;
  private static final String ARRAY_LENGTH = ".length";
  private static final Pattern BOUND =
      Pattern.compile("^([A-Za-z_]\\w*)<([A-Za-z_][\\w.]*)(\\.size\\(\\)|\\.length)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the collection
   */
  public ForLoopCanBeForeachCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return loops(root, null);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return loops(root, types);
  }

  private List<RuleMatch> loops(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
      final SyntaxNode loop = visit.node();
      final SyntaxNode control =
          loop.children().stream()
              .filter(c -> "forControl".equals(c.kind()))
              .findFirst()
              .orElse(null);
      if (control != null) {
        collectionOf(control, loop, types)
            .ifPresent(
                collection ->
                    matches.add(
                        CheckSupport.at(loop.span(), CheckSupport.fill(message, collection, ""))));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<String> collectionOf(
      final SyntaxNode control, final SyntaxNode loop, final UnitTypeFacts types) {
    final List<SyntaxNode> parts =
        control.children().stream().filter(c -> !c.kind().startsWith("'")).toList();
    if (parts.size() < CONTROL_PARTS || !"forInit".equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    final String index = indexName(parts.get(0));
    if (!SyntaxWalk.text(parts.get(0)).matches("[\\w.]*" + Pattern.quote(index) + "=0")) {
      return Optional.empty();
    }
    final String condition = SyntaxWalk.text(parts.get(1));
    final String update = SyntaxWalk.text(parts.get(2));
    if (index.isEmpty() || !update.equals(index + "++") && !update.equals("++" + index)) {
      return Optional.empty();
    }
    final Matcher bound = BOUND.matcher(condition);
    if (!bound.matches() || !bound.group(1).equals(index)) {
      return Optional.empty();
    }
    final String collection = bound.group(2);
    final SyntaxNode body = loop.children().get(loop.children().size() - 1);
    return onlyReads(body, index, collection, types, ARRAY_LENGTH.equals(bound.group(BOUND_KIND)))
        ? Optional.of(collection)
        : Optional.empty();
  }

  private static String indexName(final SyntaxNode init) {
    return SyntaxWalk.descendants(init).stream()
        .filter(n -> "variableDeclarator".equals(n.kind()))
        .findFirst()
        .flatMap(
            d ->
                SyntaxWalk.descendants(d).stream()
                    .filter(n -> "identifier".equals(n.kind()))
                    .findFirst())
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static boolean onlyReads(
      final SyntaxNode body,
      final String index,
      final String collection,
      final UnitTypeFacts types,
      final boolean array) {
    final long named =
        SyntaxWalk.descendants(body).stream()
            .filter(n -> "identifier".equals(n.kind()) && index.equals(SyntaxWalk.text(n)))
            .count();
    final Matcher reads =
        Pattern.compile(
                "(?<![\\w.])"
                    + Pattern.quote(collection)
                    + "(\\["
                    + index
                    + "\\]|\\.get\\("
                    + index
                    + "\\))"
                    + ASSIGNED)
            .matcher(SyntaxWalk.text(body));
    long allowed = 0;
    while (reads.find()) {
      allowed++;
    }
    return allowed > 0 && allowed == named && (array || iterable(body, collection, types));
  }

  private static boolean iterable(
      final SyntaxNode body, final String collection, final UnitTypeFacts types) {
    return types != null
        && SyntaxWalk.descendants(body).stream()
            .filter(n -> "expression".equals(n.kind()))
            .filter(
                n ->
                    n.children().size() > 1
                        && collection.equals(SyntaxWalk.text(n.children().get(0))))
            .anyMatch(n -> isIterable(n.children().get(0), types));
  }

  private static boolean isIterable(final SyntaxNode receiver, final UnitTypeFacts types) {
    final Optional<TypeRef> type = types.typeOf(receiver.span());
    final boolean named = type.map(t -> ITERABLES.contains(t.name())).orElse(false);
    return named
        || types
            .membersOf(receiver.span())
            .map(TypeMembers::ancestors)
            .map(a -> a.stream().anyMatch(t -> ITERABLES.contains(t.name())))
            .orElse(false);
  }
}
