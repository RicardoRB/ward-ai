package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reports the name of each declaration that passes the filter and does not match a pattern. The
 * name is the first node of {@code nameKind} below the declaration, not entering {@code skip}
 * subtrees. A declaration is left alone when it or an ancestor has a child whose kind ends in {@code
 * Attribute} whose text matches the {@code exempt} pattern, such as an {@code allow} of the lint.
 * The user option is {@code pattern}.
 */
public final class IdentifierPatternCheck implements UnitCheck {
  private static final String ATTRIBUTE = "Attribute";

  private final NodeFilter filter;
  private final String nameKind;
  private final Set<String> skip;
  private final Pattern pattern;
  private final String message;
  private final Optional<Pattern> exempt;

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
      final String message
  ) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.nameKind = Objects.requireNonNull(nameKind, "nameKind");
    this.skip = Set.copyOf(skip);
    this.pattern = Pattern.compile(Objects.requireNonNull(pattern, "pattern"));
    this.message = Objects.requireNonNull(message, "message");
    this.exempt = Optional.empty();
  }

  /**
   * Creates the check with an exempting attribute.
   *
   * @param filter the declarations to check
   * @param name where the name is
   * @param pattern the regular expression the whole name must match
   * @param message the message; {@code {value}} is the name
   * @param exempt the pattern of the attributes that exempt a declaration, as above
   * @throws IllegalArgumentException if the pattern is not a valid regular expression
   */
  public IdentifierPatternCheck(
      final NodeFilter filter,
      final Name name,
      final String pattern,
      final String message,
      final Optional<Pattern> exempt
  ) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.nameKind = name.kind();
    this.skip = Set.copyOf(name.skip());
    this.pattern = Pattern.compile(Objects.requireNonNull(pattern, "pattern"));
    this.message = Objects.requireNonNull(message, "message");
    this.exempt = Objects.requireNonNull(exempt, "exempt");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return filter.select(root, file).stream()
        .filter(visit -> !exempted(visit))
        .map(visit -> SyntaxWalk.firstDescendant(visit.node(), nameKind, skip))
        .flatMap(Optional::stream)
        .filter(name -> !pattern.matcher(SyntaxWalk.text(name)).matches())
        .map(
            name ->
                CheckSupport.at(name.span(), CheckSupport.fill(message, SyntaxWalk.text(name), ""))
        )
        .toList();
  }

  /**
   * Where the name of a declaration is.
   *
   * @param kind the kind of the node whose text is the name
   * @param skip the kinds whose subtrees are not searched for the name
   */
  public record Name(String kind, Set<String> skip) {
    /** Copies the kinds. */
    public Name {
      Objects.requireNonNull(kind, "kind");
      skip = Set.copyOf(skip);
    }
  }

  private boolean exempted(final SyntaxWalk.Visit visit) {
    return exempt.isPresent()
        && Stream.concat(visit.ancestors().stream(), Stream.of(visit.node()))
            .flatMap(node -> node.children().stream())
            .filter(child -> child.kind().endsWith(ATTRIBUTE))
            .anyMatch(child -> exempt.get().matcher(SyntaxWalk.text(child)).find());
  }
}
