package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code global} name that a function assigns while the module never defines it, like
 * Pylint {@code global-variable-undefined}. A module-level assignment, definition or import of the
 * name, anywhere at the top level, counts as a definition.
 */
public final class GlobalUndefinedCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS = Set.of(SyntaxKinds.FUNCDEF);
  private static final Set<String> GLOBALS = Set.of("global_stmt");
  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");
  private static final Set<String> DEFINITIONS = Set.of(SyntaxKinds.FUNCDEF, SyntaxKinds.CLASSDEF);
  private static final String NAME = "name";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public GlobalUndefinedCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> defined = moduleDefined(root);
    final List<String> imports = imports(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final Set<String> assigned = FunctionBodies.assignedNames(visit.node());
      for (final SyntaxNode global : FunctionBodies.ownScope(visit.node(), GLOBALS)) {
        for (final String name : globalNames(global)) {
          if (assigned.contains(name) && !defined.contains(name) && notImported(imports, name)) {
            found.add(new RuleMatch(global.span(), message));
          }
        }
      }
    }
    return List.copyOf(found);
  }

  /** The names bound at module level by assignments and by top-level definitions. */
  private static Set<String> moduleDefined(final SyntaxNode root) {
    final Set<String> names = new HashSet<>(FunctionBodies.assignedNames(root));
    for (final SyntaxNode definition : FunctionBodies.ownScope(root, DEFINITIONS)) {
      ModuleDefinitions.definedName(definition).ifPresent(names::add);
    }
    return names;
  }

  /** The texts of the module's import statements; a name in one of them counts as imported. */
  private static List<String> imports(final SyntaxNode root) {
    final List<String> texts = new ArrayList<>();
    for (final SyntaxNode statement : FunctionBodies.ownScope(root, IMPORTS)) {
      texts.add(SyntaxWalk.text(statement));
    }
    return texts;
  }

  private static boolean notImported(final List<String> imports, final String name) {
    return imports.stream().noneMatch(text -> text.contains(name));
  }

  private static List<String> globalNames(final SyntaxNode global) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode child : global.children()) {
      if (NAME.equals(child.kind())) {
        names.add(SyntaxWalk.text(child));
      }
    }
    return names;
  }
}
