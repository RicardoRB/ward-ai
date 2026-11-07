package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code @Test} import from the wrong JUnit generation, as PMD's WrongTestAnnotation
 * does: the JUnit 4 {@code org.junit.Test} in a file that also imports JUnit Jupiter, or the
 * Jupiter {@code Test} in a file that also imports other JUnit 4 types. PMD reads the framework of
 * the whole codebase from its classpath; this check reads the imports of the file.
 */
public final class WrongTestAnnotationCheck implements UnitCheck {

  private static final String JUNIT4_TEST = "org.junit.Test";
  private static final String JUPITER_TEST = "org.junit.jupiter.api.Test";
  private static final String JUPITER = "org.junit.jupiter.";
  private static final String IMPORT = "import";
  private static final Set<String> OTHER_ENGINES =
      Set.of("org.junit.jupiter.", "org.junit.platform.", "org.junit.vintage.");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the import
   */
  public WrongTestAnnotationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<Named> imports =
        SyntaxWalk.visits(root, Set.of("importDeclaration")).stream()
            .map(SyntaxWalk.Visit::node)
            .map(node -> new Named(node, importedName(node)))
            .toList();
    final boolean jupiter = imports.stream().anyMatch(i -> i.name().startsWith(JUPITER));
    final boolean junit4 = imports.stream().anyMatch(i -> isJunit4Other(i.name()));
    return imports.stream()
        .filter(
            i -> jupiter && JUNIT4_TEST.equals(i.name()) || junit4 && JUPITER_TEST.equals(i.name()))
        .map(i -> CheckSupport.at(i.node().span(), message))
        .toList();
  }

  private static boolean isJunit4Other(final String name) {
    return name.startsWith("org.junit.")
        && !JUNIT4_TEST.equals(name)
        && OTHER_ENGINES.stream().noneMatch(name::startsWith);
  }

  private static String importedName(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    return text.substring(IMPORT.length(), text.length() - 1);
  }

  private record Named(SyntaxNode node, String name) {}
}
