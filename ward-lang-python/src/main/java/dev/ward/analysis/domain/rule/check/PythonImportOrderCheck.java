package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonStdlibModules;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a module-level import that comes after an import of a later group, like pylint's {@code
 * wrong-import-order}. The groups are {@code __future__}, standard library, third party, first
 * party and relative imports, in that order. A first-party package is one that the project has a
 * file for, and everything else is third party. This is a project check: it reads the whole
 * project's trees to find the first-party packages.
 */
public final class PythonImportOrderCheck implements RuleCheck, ProjectCheck {

  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");
  private static final Set<String> SCOPES = Set.of("funcdef", "classdef");
  private static final Pattern FROM = Pattern.compile("^from([\\w.]+)import");
  private static final Pattern PLAIN = Pattern.compile("^import(\\w+)");
  private static final String FUTURE = "__future__";
  private static final String PACKAGE_SEPARATOR = ".";
  private static final String PATH_SEPARATOR = "/";
  private static final String PYTHON_SUFFIX = ".py";
  private static final int FUTURE_GROUP = 0;
  private static final int STANDARD_GROUP = 1;
  private static final int THIRD_GROUP = 2;
  private static final int FIRST_GROUP = 3;
  private static final int RELATIVE_GROUP = 4;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public PythonImportOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always empty: the order of the imports needs the first-party packages of the project. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    final Set<String> firstParty = firstPartyPackages(trees.keySet());
    final List<ProjectMatch> found = new ArrayList<>();
    for (final Map.Entry<String, SyntaxNode> entry : trees.entrySet()) {
      for (final RuleMatch match : misordered(entry.getValue(), firstParty)) {
        found.add(new ProjectMatch(entry.getKey(), match));
      }
    }
    return List.copyOf(found);
  }

  /** The top-level package or module of every project file, such as {@code app} for app/x.py. */
  private static Set<String> firstPartyPackages(final Set<String> paths) {
    final Set<String> packages = new HashSet<>();
    for (final String path : paths) {
      final String first =
          path.contains(PATH_SEPARATOR) ? path.substring(0, path.indexOf('/')) : path;
      packages.add(
          first.endsWith(PYTHON_SUFFIX)
              ? first.substring(0, first.length() - PYTHON_SUFFIX.length())
              : first);
    }
    return packages;
  }

  private List<RuleMatch> misordered(final SyntaxNode root, final Set<String> firstParty) {
    final List<RuleMatch> found = new ArrayList<>();
    int highest = FUTURE_GROUP;
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, IMPORTS)) {
      final Optional<Integer> group = groupOf(visit, firstParty);
      if (group.isEmpty()) {
        continue;
      }
      if (group.get() < highest) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
      highest = Math.max(highest, group.get());
    }
    return found;
  }

  /** The group of a module-level import, or empty for an import inside a function or class. */
  private static Optional<Integer> groupOf(
      final SyntaxWalk.Visit visit, final Set<String> firstParty) {
    if (visit.ancestors().stream().anyMatch(ancestor -> SCOPES.contains(ancestor.kind()))) {
      return Optional.empty();
    }
    final String text = SyntaxWalk.text(visit.node());
    final Matcher from = FROM.matcher(text);
    if (from.find()) {
      return Optional.of(
          from.group(1).startsWith(PACKAGE_SEPARATOR)
              ? RELATIVE_GROUP
              : groupOfModule(from.group(1), firstParty));
    }
    final Matcher plain = PLAIN.matcher(text);
    return plain.find() ? Optional.of(groupOfModule(plain.group(1), firstParty)) : Optional.empty();
  }

  private static int groupOfModule(final String module, final Set<String> firstParty) {
    final int dot = module.indexOf(PACKAGE_SEPARATOR);
    final String top = dot < 0 ? module : module.substring(0, dot);
    if (FUTURE.equals(top)) {
      return FUTURE_GROUP;
    }
    if (PythonStdlibModules.NAMES.contains(top)) {
      return STANDARD_GROUP;
    }
    return firstParty.contains(top) ? FIRST_GROUP : THIRD_GROUP;
  }
}
