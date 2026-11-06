package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports each name that no scope declares and no built-in provides, like pyflakes {@code F821} and
 * ESLint {@code no-undef}. A name in an {@linkplain dev.ward.analysis.domain.scope.Scope#open()
 * open} scope is left alone, and so is a Java, Kotlin or Swift name that starts with an upper-case
 * letter, which may be a type that a wildcard import brings in, or that a file around this one in
 * the same package or module declares.
 */
public final class UndefinedNameCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final Set<AnalysisLanguage> PACKAGE_TYPES =
      Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN, AnalysisLanguage.SWIFT);

  private final String message;
  private final Set<String> environments;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   * @param environments the environments whose built-in names are defined, such as {@code browser};
   *     empty means the language's defaults
   */
  public UndefinedNameCheck(final String message, final Set<String> environments) {
    this.message = message;
    this.environments = Set.copyOf(environments);
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the undefined-name check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(environments);
    final boolean packageTypes = PACKAGE_TYPES.contains(unit.language());
    final List<RuleMatch> found = new ArrayList<>();
    for (final NameReference reference : scopes.unresolved()) {
      if (!scopes.isOpen(reference)
          && !(packageTypes && visibleInPackage(reference.name(), view))) {
        found.add(new RuleMatch(reference.span(), message.replace(NAME, reference.name())));
      }
    }
    return found;
  }

  /**
   * A name that may be a type of the package or a declaration of a file around this one. Upper-case
   * names stand for types the file's wildcard imports may bring in.
   */
  private static boolean visibleInPackage(final String name, final ScopeView view) {
    return Character.isUpperCase(name.charAt(0)) || view.projectNames().contains(name);
  }
}
