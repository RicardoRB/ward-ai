package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Checks that an outer type is named as its file, as Checkstyle's OuterTypeFilename does: the first
 * outer type, or a public one, must carry the file name. A file with no outer type passes.
 */
public final class OuterTypeFilenameCheck implements UnitCheck {

  private static final String EXTENSION = ".java";
  private static final String DECLARATION = "typeDeclaration";
  private static final String MODIFIER = "classOrInterfaceModifier";
  private static final String NAME = "identifier";
  private static final String PUBLIC = "public";
  private static final Set<String> SKIPPED = Set.of(MODIFIER, "annotation");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the name of the misnamed type
   */
  public OuterTypeFilenameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final String name = file.name();
    if (!name.endsWith(EXTENSION)) {
      return List.of();
    }
    final String expected = name.substring(0, name.length() - EXTENSION.length());
    final List<SyntaxNode> types =
        root.children().stream().filter(OuterTypeFilenameCheck::named).toList();
    final List<SyntaxNode> candidates = new ArrayList<>();
    for (int index = 0; index < types.size(); index++) {
      final SyntaxNode type = types.get(index);
      if (index == 0 || isPublic(type)) {
        if (expected.equals(typeName(type))) {
          return List.of();
        }
        candidates.add(type);
      }
    }
    if (candidates.isEmpty()) {
      return List.of();
    }
    final SyntaxNode wrong = candidates.get(candidates.size() - 1);
    return List.of(
        CheckSupport.at(
            identifier(wrong).orElseThrow().span(),
            CheckSupport.fill(message, typeName(wrong), expected)));
  }

  private static boolean named(final SyntaxNode type) {
    return identifier(type).isPresent();
  }

  private static String typeName(final SyntaxNode type) {
    return SyntaxWalk.text(identifier(type).orElseThrow());
  }

  private static Optional<SyntaxNode> identifier(final SyntaxNode type) {
    return DECLARATION.equals(type.kind())
        ? SyntaxWalk.firstDescendant(type, NAME, SKIPPED)
        : Optional.empty();
  }

  private static boolean isPublic(final SyntaxNode type) {
    return type.children().stream()
        .anyMatch(child -> MODIFIER.equals(child.kind()) && PUBLIC.equals(SyntaxWalk.text(child)));
  }
}
