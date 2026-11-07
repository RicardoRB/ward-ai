package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each type reference written with its package when the simple name would resolve to the
 * same type: the type is imported, lies in {@code java.lang}, or lies in the unit's own package.
 */
public final class UnnecessaryFullyQualifiedTypeCheck implements RuleCheck {

  private static final String TYPE_KIND = "classType";
  private static final String PACKAGE_KIND = "packageName";
  private static final String TYPE_NAME_KIND = "typeIdentifier";
  private static final String JAVA_LANG = "java.lang";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the qualified name
   */
  public UnnecessaryFullyQualifiedTypeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Optional<String> unitPackage = ImportDeclaration.packageOf(root);
    final Set<String> imported = importedClasses(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(TYPE_KIND))) {
      final Optional<String> qualified = qualifiedName(visit.node());
      if (qualified.isPresent() && unnecessary(qualified.get(), imported, unitPackage)) {
        matches.add(
            CheckSupport.at(visit.node().span(), CheckSupport.fill(message, qualified.get(), "")));
      }
    }
    return List.copyOf(matches);
  }

  private static Set<String> importedClasses(final SyntaxNode root) {
    final Set<String> imported = new HashSet<>();
    for (final ImportDeclaration declaration : ImportDeclaration.declarations(root)) {
      if (!declaration.isStatic() && !declaration.star()) {
        imported.add(declaration.name());
      }
    }
    return imported;
  }

  /**
   * Returns the package and first type name of a type reference, such as {@code java.util.List}.
   */
  private static Optional<String> qualifiedName(final SyntaxNode type) {
    final Optional<SyntaxNode> pkg = child(type, PACKAGE_KIND);
    final Optional<SyntaxNode> simple = child(type, TYPE_NAME_KIND);
    if (pkg.isEmpty() || simple.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(SyntaxWalk.text(pkg.get()) + "." + SyntaxWalk.text(simple.get()));
  }

  private static boolean unnecessary(
      final String qualified, final Set<String> imported, final Optional<String> unitPackage) {
    final int dot = qualified.lastIndexOf('.');
    final String pkg = qualified.substring(0, dot);
    return imported.contains(qualified)
        || JAVA_LANG.equals(pkg)
        || unitPackage.map(pkg::equals).orElse(false);
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }
}
