package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reports each import of an illegal package or class. A package matches the names in it and in its
 * subpackages; a class matches its name. With {@code regexp}, each entry is a pattern that a name
 * matches when it is found in it.
 */
public final class IllegalImportCheck implements RuleCheck {

  private final List<String> packages;
  private final List<String> classes;
  private final boolean regexp;
  private final String message;

  /**
   * Creates the check.
   *
   * @param packages the illegal packages, or patterns when {@code regexp}
   * @param classes the illegal classes, or patterns when {@code regexp}
   * @param regexp whether the entries are regular expressions
   * @param message the message; {@code {value}} is the imported name
   * @throws IllegalArgumentException if an entry is not a valid regular expression
   */
  public IllegalImportCheck(
      final List<String> packages,
      final List<String> classes,
      final boolean regexp,
      final String message) {
    this.packages = List.copyOf(packages);
    this.classes = List.copyOf(classes);
    this.regexp = regexp;
    this.message = Objects.requireNonNull(message, "message");
    if (regexp) {
      this.packages.forEach(Pattern::compile);
      this.classes.forEach(Pattern::compile);
    }
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final ImportDeclaration declaration : ImportDeclaration.declarations(root)) {
      if (illegal(declaration.name())) {
        matches.add(
            CheckSupport.at(
                declaration.span(), CheckSupport.fill(message, declaration.fullName(), "")));
      }
    }
    return List.copyOf(matches);
  }

  private boolean illegal(final String name) {
    return packages.stream().anyMatch(entry -> inPackage(name, entry))
        || classes.stream().anyMatch(entry -> sameName(name, entry));
  }

  private boolean inPackage(final String name, final String entry) {
    return regexp ? find(entry, name) : name.equals(entry) || name.startsWith(entry + ".");
  }

  private boolean sameName(final String name, final String entry) {
    return regexp ? find(entry, name) : name.equals(entry);
  }

  private static boolean find(final String regex, final String name) {
    return Pattern.compile(regex).matcher(name).find();
  }
}
