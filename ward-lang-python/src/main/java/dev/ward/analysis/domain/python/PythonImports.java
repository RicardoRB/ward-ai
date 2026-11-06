package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Declares the names of Python {@code import} statements. A star import opens the scope it is in,
 * because it brings in names that the file does not spell.
 */
final class PythonImports {

  private static final String STAR = "*";
  private static final String DOT = ".";
  private static final String IMPORT = "import";
  private static final String DOTTED_NAME = "dotted_name";

  private final ScopeBuilder builder;
  private final Set<String> exported;

  PythonImports(final ScopeBuilder builder, final Set<String> exported) {
    this.builder = builder;
    this.exported = exported;
  }

  /** {@code import a.b as c}: binds {@code c}, or {@code a} without an alias. */
  void importName(final SyntaxNode statement) {
    PythonSyntax.children(statement, "dotted_as_names").stream()
        .flatMap(names -> PythonSyntax.children(names, "dotted_as_name").stream())
        .forEach(this::dottedAlias);
  }

  private void dottedAlias(final SyntaxNode alias) {
    final SyntaxNode module = PythonSyntax.children(alias, DOTTED_NAME).get(0);
    final List<SyntaxNode> aliased = PythonSyntax.children(alias, PythonNames.NAME);
    final SyntaxNode bound =
        aliased.isEmpty() ? PythonSyntax.children(module, PythonNames.NAME).get(0) : aliased.get(0);
    declareImport(bound, PythonSyntax.dottedName(module), "", alias.span());
  }

  /** {@code from m import a as b}: binds {@code b}, or {@code a} without an alias. */
  void importFrom(final SyntaxNode statement) {
    final String module = fromModule(statement);
    if (statement.children().stream().anyMatch(child -> STAR.equals(PythonSyntax.text(child)))) {
      builder.markOpen(builder.current());
      return;
    }
    PythonSyntax.children(statement, "import_as_names").stream()
        .flatMap(names -> PythonSyntax.children(names, "import_as_name").stream())
        .forEach(alias -> fromAlias(alias, module, statement.span()));
  }

  private void fromAlias(final SyntaxNode alias, final String module, final Span site) {
    final List<SyntaxNode> names = PythonSyntax.children(alias, PythonNames.NAME);
    final String member = PythonNames.identifier(names.get(0));
    declareImport(names.get(names.size() - 1), module, member, site);
  }

  /** The module of a {@code from} import as written, with its leading dots. */
  private static String fromModule(final SyntaxNode statement) {
    final StringBuilder module = new StringBuilder();
    for (final SyntaxNode child : statement.children()) {
      final String text = PythonSyntax.text(child);
      if (text.startsWith(DOT)) {
        module.append(text);
      } else if (DOTTED_NAME.equals(child.kind())) {
        module.append(PythonSyntax.dottedName(child));
      } else if (IMPORT.equals(text)) {
        break;
      }
    }
    return module.toString();
  }

  private void declareImport(
      final SyntaxNode bound, final String module, final String member, final Span site) {
    final String identifier = PythonNames.identifier(bound);
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (builder.current() == UnitScopes.MODULE_ID && exported.contains(identifier)) {
      flags.add(BindingFlag.EXPORTED);
    }
    builder.declareImport(identifier, bound.span(), new ImportOrigin(module, member, site), flags);
    builder.reference(identifier, bound.span(), ReferenceAccess.WRITE);
  }
}
