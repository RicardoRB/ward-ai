package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the name of each declaration that passes the filter against a pattern. The name is the
 * text of the first node of {@code nameKind} below the declaration, not entering {@code skip}
 * subtrees.
 */
public final class TextPatternCheck implements RuleCheck {

  private final DeclarationFilter filter;
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
  public TextPatternCheck(
      final DeclarationFilter filter,
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
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, filter.kinds())) {
      final Declaration declaration = Declaration.of(visit, filter.modifierKinds());
      if (CheckSupport.admits(declaration.modifiers(), filter.required(), filter.excluded())) {
        check(visit.node(), matches);
      }
    }
    return List.copyOf(matches);
  }

  private void check(final SyntaxNode declaration, final List<RuleMatch> matches) {
    final Optional<SyntaxNode> name = SyntaxWalk.firstDescendant(declaration, nameKind, skip);
    if (name.isEmpty()) {
      return;
    }
    final String text = SyntaxWalk.text(name.get());
    if (!pattern.matcher(text).matches()) {
      matches.add(new RuleMatch(name.get().span(), CheckSupport.fill(message, text, "")));
    }
  }
}
