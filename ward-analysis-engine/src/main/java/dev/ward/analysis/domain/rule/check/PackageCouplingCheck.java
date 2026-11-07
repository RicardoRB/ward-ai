package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each import of a class from a configured package hierarchy when the file lies outside
 * that hierarchy, as PMD's loose package coupling does. No packages means nothing is reported.
 */
public final class PackageCouplingCheck implements UnitCheck {

  private static final String UNIT = "compilationunit";
  private static final String IMPORT = "importDeclaration";
  private static final String PACKAGE = "packageDeclaration";
  private static final String IMPORT_STATIC = "importstatic";
  private static final String IMPORT_PREFIX = "import";
  private static final String PACKAGE_PREFIX = "package";

  private final List<String> packages;
  private final String message;

  /**
   * Creates the check.
   *
   * @param packages the package hierarchies whose imports are reported from outside them
   * @param message the message; {@code {value}} is the imported name
   */
  public PackageCouplingCheck(final List<String> packages, final String message) {
    this.packages = List.copyOf(packages);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    if (packages.isEmpty()) {
      return List.of();
    }
    return SyntaxWalk.visits(root, Set.of(UNIT)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(unit -> reportsOf(unit).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode unit) {
    final String own = packageOf(unit);
    return unit.children().stream()
        .filter(child -> IMPORT.equals(child.kind()))
        .filter(imported -> isOutside(importedName(imported), own))
        .map(
            imported ->
                CheckSupport.at(
                    imported.span(), message.replace("{value}", importedName(imported))))
        .toList();
  }

  private boolean isOutside(final String imported, final String own) {
    return packages.stream()
        .filter(pkg -> imported.startsWith(pkg + "."))
        .anyMatch(pkg -> !own.equals(pkg) && !own.startsWith(pkg + "."));
  }

  private static String packageOf(final SyntaxNode unit) {
    return unit.children().stream()
        .filter(child -> PACKAGE.equals(child.kind()))
        .findFirst()
        .map(child -> trimmed(SyntaxWalk.text(child), PACKAGE_PREFIX))
        .orElse("");
  }

  private static String importedName(final SyntaxNode imported) {
    final String text = SyntaxWalk.text(imported);
    final String withoutStatic =
        text.startsWith(IMPORT_STATIC) ? text.substring(IMPORT_STATIC.length()) : text;
    return trimmed(withoutStatic, IMPORT_PREFIX);
  }

  private static String trimmed(final String text, final String prefix) {
    final String body = text.startsWith(prefix) ? text.substring(prefix.length()) : text;
    return body.endsWith(";") ? body.substring(0, body.length() - 1) : body;
  }
}
