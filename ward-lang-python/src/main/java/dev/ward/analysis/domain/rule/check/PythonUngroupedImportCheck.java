package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a module-level import whose top-level package was imported earlier but not by the import
 * just before it, like pylint's {@code ungrouped-imports}. Imports inside functions and classes are
 * left alone, and so are relative imports.
 */
public final class PythonUngroupedImportCheck implements RuleCheck {

  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");
  private static final Set<String> SCOPES = Set.of("funcdef", "classdef");
  private static final Pattern FROM = Pattern.compile("^from([\\w.]+)import");
  private static final Pattern PLAIN = Pattern.compile("^import(\\w+)");
  private static final String PACKAGE_SEPARATOR = ".";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public PythonUngroupedImportCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    String previous = null;
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, IMPORTS)) {
      final Optional<String> packageName = packageOf(visit);
      if (packageName.isEmpty()) {
        continue;
      }
      final String name = packageName.get();
      if (seen.contains(name) && !name.equals(previous)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
      seen.add(name);
      previous = name;
    }
    return List.copyOf(found);
  }

  /** The top-level package an import reads, or empty for a function-level or relative import. */
  private static Optional<String> packageOf(final SyntaxWalk.Visit visit) {
    if (visit.ancestors().stream().anyMatch(ancestor -> SCOPES.contains(ancestor.kind()))) {
      return Optional.empty();
    }
    final String text = SyntaxWalk.text(visit.node());
    final Matcher from = FROM.matcher(text);
    if (from.find()) {
      return topLevel(from.group(1));
    }
    final Matcher plain = PLAIN.matcher(text);
    return plain.find() ? topLevel(plain.group(1)) : Optional.empty();
  }

  private static Optional<String> topLevel(final String module) {
    if (module.startsWith(PACKAGE_SEPARATOR)) {
      return Optional.empty();
    }
    final int dot = module.indexOf(PACKAGE_SEPARATOR);
    return Optional.of(dot < 0 ? module : module.substring(0, dot));
  }
}
