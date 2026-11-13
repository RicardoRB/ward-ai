package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.Set;

/**
 * The modifier words written before a Kotlin declaration, such as {@code internal} or {@code open}.
 */
final class KotlinApiModifiers {
  private static final Set<String> LISTS = Set.of("modifierList", "functionModifierList");
  private static final String MODIFIER = "modifier";

  private final Set<String> words;

  private KotlinApiModifiers(final Set<String> words) {
    this.words = Set.copyOf(words);
  }

  /** Returns the modifiers of {@code declaration}; annotations are not modifiers. */
  static KotlinApiModifiers of(final SyntaxNode declaration) {
    final Set<String> found = new HashSet<>();
    declaration.children().stream()
        .filter(child -> LISTS.contains(child.kind()))
        .flatMap(list -> Syntax.children(list, MODIFIER).stream())
        .flatMap(modifier -> Syntax.tokens(modifier).stream())
        .map(String::strip)
        .filter(word -> !word.isEmpty())
        .forEach(found::add);
    return new KotlinApiModifiers(found);
  }

  boolean has(final String word) {
    return words.contains(word);
  }

  /** Returns the visibility: public unless written otherwise; {@code internal} is module-only. */
  ApiVisibility visibility() {
    if (has("private")) {
      return ApiVisibility.PRIVATE;
    }
    if (has("internal")) {
      return ApiVisibility.PACKAGE;
    }
    return has("protected") ? ApiVisibility.PROTECTED : ApiVisibility.PUBLIC;
  }

  /** Returns the written visibility, or {@code fallback} when the declaration writes none. */
  ApiVisibility visibilityOr(final ApiVisibility fallback) {
    final boolean written = has("private") || has("internal") || has("protected") || has("public");
    return written ? visibility() : fallback;
  }
}
