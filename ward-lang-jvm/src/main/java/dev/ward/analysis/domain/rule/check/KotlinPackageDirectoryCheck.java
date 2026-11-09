package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a Kotlin package declaration that does not match the directory of its file, as detekt's
 * {@code invalid-package-declaration} does. The package minus the root package must be the end of
 * the file's directory path. A file without a path is not checked.
 */
public final class KotlinPackageDirectoryCheck implements UnitCheck {

  private static final String PACKAGE = "packageHeader";
  private static final String IDENTIFIER = "identifier";
  private static final String DOT = ".";
  private static final String SEPARATOR = "/";
  private static final String MISSING_ROOT = "The package declaration is missing the root package";
  private static final String MISMATCH =
      "The package declaration does not match the actual file location.";

  private final String rootPackage;
  private final boolean requireRoot;

  /**
   * Creates the check.
   *
   * @param rootPackage the package prefix that is not part of the directory, or empty
   * @param requireRoot whether a package must start with the root package
   */
  public KotlinPackageDirectoryCheck(final String rootPackage, final boolean requireRoot) {
    this.rootPackage = Objects.requireNonNull(rootPackage, "rootPackage");
    this.requireRoot = requireRoot;
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    if (file.path().isEmpty()) {
      return List.of();
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(PACKAGE))) {
      final String pkg = packageName(visit.node());
      if (!pkg.isEmpty()) {
        checkPackage(visit.node(), pkg, file.path(), matches);
      }
    }
    return List.copyOf(matches);
  }

  private static String packageName(final SyntaxNode header) {
    return header.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(identifier -> SyntaxWalk.text(identifier).replaceAll("\\s", ""))
        .orElse("");
  }

  private void checkPackage(
      final SyntaxNode header, final String pkg, final String path, final List<RuleMatch> out) {
    if (requireRoot && !pkg.startsWith(rootPackage)) {
      out.add(CheckSupport.at(header.span(), MISSING_ROOT));
      return;
    }
    final List<String> expected = relativeSegments(pkg);
    if (!expected.isEmpty() && !endsWith(directories(path), expected)) {
      out.add(CheckSupport.at(header.span(), MISMATCH));
    }
  }

  private List<String> relativeSegments(final String pkg) {
    final String rest = pkg.startsWith(rootPackage) ? pkg.substring(rootPackage.length()) : pkg;
    final String trimmed = rest.startsWith(DOT) ? rest.substring(1) : rest;
    return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\."));
  }

  private static List<String> directories(final String path) {
    final int slash = path.lastIndexOf(SEPARATOR);
    if (slash < 0) {
      return List.of();
    }
    return Arrays.asList(path.substring(0, slash).split(SEPARATOR));
  }

  private static boolean endsWith(final List<String> dirs, final List<String> expected) {
    return dirs.size() >= expected.size()
        && dirs.subList(dirs.size() - expected.size(), dirs.size()).equals(expected);
  }
}
