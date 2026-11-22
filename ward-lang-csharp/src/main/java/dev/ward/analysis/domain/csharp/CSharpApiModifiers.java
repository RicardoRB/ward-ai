package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** The modifiers written before a C# declaration, with the accessibility they give. */
final class CSharpApiModifiers {
  private static final String PUBLIC = "public";
  private static final String PROTECTED = "protected";
  private static final String INTERNAL = "internal";
  private static final String PRIVATE = "private";

  private static final Set<String> ACCESS = Set.of(PUBLIC, PROTECTED, INTERNAL, PRIVATE);

  private final Set<String> words;

  /** Reads the {@code modifier} children of {@code declaration}. */
  CSharpApiModifiers(final SyntaxNode declaration) {
    this(
        CSharpSyntax.children(declaration, "modifier").stream()
            .map(CSharpSyntax::text)
            .collect(Collectors.toSet())
    );
  }

  private CSharpApiModifiers(final Set<String> words) {
    this.words = new HashSet<>(words);
  }

  /** Returns the modifiers of a declaration the compiler writes, such as {@code public}. */
  static CSharpApiModifiers of(final String word) {
    return new CSharpApiModifiers(Set.of(word));
  }

  /**
   * Returns the modifiers of an accessor of a property with these modifiers: the accessibility the
   * accessor writes replaces the property's, and the rest is the property's.
   */
  CSharpApiModifiers withAccessor(final SyntaxNode accessor) {
    final Set<String> written =
        CSharpSyntax.children(accessor, "accessor_modifier").stream()
            .map(CSharpSyntax::text)
            .filter(ACCESS::contains)
            .collect(Collectors.toSet());
    if (written.isEmpty()) {
      return this;
    }
    final Set<String> merged = new HashSet<>(words);
    merged.removeAll(ACCESS);
    merged.addAll(written);
    return new CSharpApiModifiers(merged);
  }

  boolean has(final String word) {
    return words.contains(word);
  }

  /**
   * Returns who can see the declaration: {@code protected internal} counts as protected, because a
   * subclass in another assembly sees it; {@code internal} and {@code private protected} stay in
   * the assembly.
   */
  ApiVisibility visibility(final ApiVisibility declared) {
    if (words.contains(PUBLIC)) {
      return ApiVisibility.PUBLIC;
    }
    if (words.contains(PROTECTED) && words.contains(PRIVATE)) {
      return ApiVisibility.PACKAGE;
    }
    if (words.contains(PROTECTED)) {
      return ApiVisibility.PROTECTED;
    }
    if (words.contains(INTERNAL)) {
      return ApiVisibility.PACKAGE;
    }
    return words.contains(PRIVATE) ? ApiVisibility.PRIVATE : declared;
  }
}
