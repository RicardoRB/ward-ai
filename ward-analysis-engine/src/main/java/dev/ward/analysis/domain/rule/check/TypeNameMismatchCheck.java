package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each top-level type whose name differs from the file name, without the {@code .java}
 * extension. A file with no name, such as a bare syntax tree, is not checked.
 */
public final class TypeNameMismatchCheck implements UnitCheck {

  private static final String TYPE_DECLARATION = "typeDeclaration";

  private static final String EXTENSION = ".java";
  private static final Set<String> TYPES =
      Set.of("classDeclaration", "interfaceDeclaration", "enumDeclaration", "recordDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the type name
   */
  public TypeNameMismatchCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final String base = baseName(file.name());
    final List<RuleMatch> matches = new ArrayList<>();
    if (base.isEmpty()) {
      return List.of();
    }
    for (final SyntaxNode unit : root.children()) {
      if (TYPE_DECLARATION.equals(unit.kind())) {
        topLevelMismatch(unit, base, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void topLevelMismatch(
      final SyntaxNode declaration, final String base, final List<RuleMatch> matches) {
    for (final SyntaxNode child : declaration.children()) {
      if (TYPES.contains(child.kind())) {
        final String name =
            child.children().stream()
                .filter(c -> "identifier".equals(c.kind()))
                .findFirst()
                .map(SyntaxWalk::text)
                .orElse("");
        if (!name.equals(base)) {
          matches.add(CheckSupport.at(declaration.span(), CheckSupport.fill(message, name, "")));
        }
      }
    }
  }

  private static String baseName(final String fileName) {
    return fileName.endsWith(EXTENSION)
        ? fileName.substring(0, fileName.length() - EXTENSION.length())
        : fileName;
  }
}
