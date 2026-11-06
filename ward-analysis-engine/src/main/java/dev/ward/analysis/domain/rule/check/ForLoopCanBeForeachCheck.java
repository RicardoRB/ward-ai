package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a counting loop over a collection or array whose index only reads elements, as {@code
 * list.get(i)} or {@code array[i]}. The index must start at zero, count up to the size or length of
 * the same collection, and appear nowhere else in the body.
 */
public final class ForLoopCanBeForeachCheck implements UnitCheck {

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
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("statement"))) {
      final SyntaxNode loop = visit.node();
      final SyntaxNode control =
          loop.children().stream()
              .filter(c -> "forControl".equals(c.kind()))
              .findFirst()
              .orElse(null);
      if (control != null) {
        collectionOf(control, loop)
            .ifPresent(
                collection ->
                    matches.add(
                        CheckSupport.at(loop.span(), CheckSupport.fill(message, collection, ""))));
      }
    }
    return List.copyOf(matches);
  }

  private static java.util.Optional<String> collectionOf(
      final SyntaxNode control, final SyntaxNode loop) {
    final List<SyntaxNode> parts =
        control.children().stream().filter(c -> !c.kind().startsWith("'")).toList();
    if (parts.size() < 3 || !"forInit".equals(parts.get(0).kind())) {
      return java.util.Optional.empty();
    }
    final String index = indexName(parts.get(0));
    final String condition = SyntaxWalk.text(parts.get(1));
    final String update = SyntaxWalk.text(parts.get(2));
    if (index.isEmpty() || !update.equals(index + "++") && !update.equals("++" + index)) {
      return java.util.Optional.empty();
    }
    final Matcher bound = BOUND.matcher(condition);
    if (!bound.matches() || !bound.group(1).equals(index)) {
      return java.util.Optional.empty();
    }
    final String collection = bound.group(2);
    final SyntaxNode body = loop.children().get(loop.children().size() - 1);
    return onlyReads(body, index, collection)
        ? java.util.Optional.of(collection)
        : java.util.Optional.empty();
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
      final SyntaxNode body, final String index, final String collection) {
    final long named =
        SyntaxWalk.descendants(body).stream()
            .filter(n -> "identifier".equals(n.kind()) && index.equals(SyntaxWalk.text(n)))
            .count();
    final Matcher reads =
        Pattern.compile(
                Pattern.quote(collection) + "(\\[" + index + "\\]|\\.get\\(" + index + "\\))")
            .matcher(SyntaxWalk.text(body));
    long allowed = 0;
    while (reads.find()) {
      allowed++;
    }
    return allowed > 0 && allowed == named;
  }
}
