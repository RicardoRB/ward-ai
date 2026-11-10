package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a declaration that has an annotation together with a modifier that the annotation's own
 * {@code @IncompatibleModifiers} forbids, as Error Prone's IncompatibleModifiers does. Only
 * annotations declared in the same file are known.
 */
public final class JavaIncompatibleModifiersCheck implements RuleCheck {

  private static final Pattern MODIFIER = Pattern.compile("Modifier\\.([A-Z_]+)");
  private static final Set<String> HOLDERS =
      Set.of("classBodyDeclaration", "typeDeclaration", "interfaceBodyDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the annotation and {@code {max}} the modifier
   */
  public JavaIncompatibleModifiersCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, Set<String>> forbidden = forbidden(root);
    final List<RuleMatch> matches = new ArrayList<>();
    if (!forbidden.isEmpty()) {
      for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, HOLDERS)) {
        check(visit.node(), forbidden, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void check(
      final SyntaxNode holder,
      final Map<String, Set<String>> forbidden,
      final List<RuleMatch> matches) {
    final List<String> keywords = JavaAnnotations.keywords(holder);
    JavaAnnotations.of(holder)
        .forEach(annotation -> annotated(annotation, keywords, forbidden, matches));
  }

  private void annotated(
      final SyntaxNode annotation,
      final List<String> keywords,
      final Map<String, Set<String>> forbidden,
      final List<RuleMatch> matches) {
    final String name = JavaAnnotations.simpleName(annotation);
    forbidden.getOrDefault(name, Set.of()).stream()
        .filter(keywords::contains)
        .forEach(
            modifier ->
                matches.add(
                    CheckSupport.at(
                        annotation.span(), CheckSupport.fill(message, name, modifier))));
  }

  private static Map<String, Set<String>> forbidden(final SyntaxNode root) {
    final Map<String, Set<String>> map = new HashMap<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of("annotationTypeDeclaration"))) {
      final Set<String> modifiers = incompatible(visit);
      if (!modifiers.isEmpty()) {
        map.put(JavaNodes.name(visit.node()), modifiers);
      }
    }
    return map;
  }

  private static Set<String> incompatible(final SyntaxWalk.Visit visit) {
    final Set<String> modifiers = new HashSet<>();
    JavaNodes.modifiers(visit).stream()
        .filter(text -> text.startsWith("@IncompatibleModifiers"))
        .forEach(text -> collect(text, modifiers));
    return modifiers;
  }

  private static void collect(final String annotation, final Set<String> modifiers) {
    final Matcher found = MODIFIER.matcher(annotation);
    while (found.find()) {
      modifiers.add(found.group(1).toLowerCase(Locale.ROOT));
    }
  }
}
