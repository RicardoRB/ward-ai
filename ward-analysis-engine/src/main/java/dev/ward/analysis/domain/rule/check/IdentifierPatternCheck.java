package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports the name of each declaration that passes the filter and does not match a pattern. The
 * name is the first node of {@code nameKind} below the declaration, not entering {@code skip}
 * subtrees. The user option is {@code pattern}.
 */
public final class IdentifierPatternCheck implements UnitCheck {

  private final NodeFilter filter;
  private final String nameKind;
  private final Set<String> skip;
  private final Pattern pattern;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the declarations to check
   * @param nameKind the kind of the node whose text is the name
   * @param skip the kinds whose subtrees are not searched for the name
   * @param pattern the regular expression the whole name must match
   * @param message the message; {@code {value}} is the name
   * @throws IllegalArgumentException if the pattern is not a valid regular expression
   */
  public IdentifierPatternCheck(
      final NodeFilter filter,
      final String nameKind,
      final Set<String> skip,
      final String pattern,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.nameKind = Objects.requireNonNull(nameKind, "nameKind");
    this.skip = Set.copyOf(skip);
    this.pattern = Pattern.compile(Objects.requireNonNull(pattern, "pattern"));
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return filter.select(root, file).stream()
        .map(visit -> SyntaxWalk.firstDescendant(visit.node(), nameKind, skip))
        .flatMap(Optional::stream)
        .filter(name -> !pattern.matcher(SyntaxWalk.text(name)).matches())
        .map(
            name ->
                CheckSupport.at(name.span(), CheckSupport.fill(message, SyntaxWalk.text(name), "")))
        .toList();
  }
}
