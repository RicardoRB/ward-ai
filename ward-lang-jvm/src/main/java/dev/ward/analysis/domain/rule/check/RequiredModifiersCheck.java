package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a declaration annotated with an annotation type of the same file that is itself marked
 * {@code @RequiredModifiers}, when the declaration lacks one of the modifiers asked for. An
 * annotation type declared elsewhere is not known, so it is not checked.
 */
public final class RequiredModifiersCheck implements RuleCheck {

  private static final String ANNOTATION_TYPE = "annotationTypeDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String REQUIRED = "RequiredModifiers";
  private static final Set<String> DECLARATIONS = Set.of("classBodyDeclaration", "typeDeclaration");
  private static final Pattern MODIFIER = Pattern.compile("(?:Modifier\\.)?([A-Z]+)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the declaration
   */
  public RequiredModifiersCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, Set<String>> required = required(root);
    if (required.isEmpty()) {
      return List.of();
    }
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, DECLARATIONS)) {
      final List<String> modifiers = MemberDeclarations.modifiers(visit.node());
      final boolean lacking =
          AnnotationTexts.of(visit.node()).stream()
              .map(AnnotationTexts::simpleName)
              .map(required::get)
              .filter(Objects::nonNull)
              .anyMatch(wanted -> !modifiers.containsAll(wanted));
      if (lacking) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static Map<String, Set<String>> required(final SyntaxNode root) {
    final Map<String, Set<String>> result = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION_TYPE))) {
      final String name =
          visit.node().children().stream()
              .filter(child -> IDENTIFIER.equals(child.kind()))
              .findFirst()
              .map(SyntaxWalk::text)
              .orElse("");
      visit.ancestors().stream()
          .reduce((first, second) -> second)
          .map(AnnotationTexts::of)
          .orElse(List.of())
          .stream()
          .filter(text -> REQUIRED.equals(AnnotationTexts.simpleName(text)))
          .forEach(text -> result.put(name, modifiersIn(text)));
    }
    return result;
  }

  private static Set<String> modifiersIn(final String annotation) {
    final Matcher found = MODIFIER.matcher(annotation.substring(annotation.indexOf('(') + 1));
    final Set<String> modifiers = new java.util.HashSet<>();
    while (found.find()) {
      modifiers.add(found.group(1).toLowerCase(Locale.ROOT));
    }
    return modifiers;
  }
}
