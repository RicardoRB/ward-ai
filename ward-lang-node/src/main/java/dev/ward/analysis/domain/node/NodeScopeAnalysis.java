package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The lexical scopes of a TypeScript or JavaScript file. Imports are read from the tokens, so
 * {@code import type} and type-only specifiers bind type-only names. A {@code with} statement and a
 * call of {@code eval} make a scope open.
 */
public final class NodeScopeAnalysis implements ScopeAnalysis {

  private final BuiltinNames builtins;

  /** Creates the analysis over the global names of the JavaScript environments. */
  public NodeScopeAnalysis(final BuiltinNames builtins) {
    this.builtins = builtins;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final ScopeBuilder builder = new ScopeBuilder(tree.span(), ClassScopes.VISIBLE);
    final NodeImportScan imports = new NodeImportScan(tree);
    final Set<String> globals = new LinkedHashSet<>(NodeGlobalComments.of(unit.text()));
    globals.addAll(NodeTestGlobals.of(unit.path()));
    globals.forEach(
        name ->
            builder.declareIn(
                UnitScopes.BUILTIN_ID, name, BindingKind.BUILTIN, UnitScopes.NOWHERE, Set.of()));
    imports.bindings().forEach(binding -> declareImport(builder, binding));
    new NodeScopeWalk(builder, imports.sites()).visit(tree);
    return builder.build();
  }

  private static void declareImport(
      final ScopeBuilder builder, final NodeImportScan.Binding binding) {
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (binding.typeOnly()) {
      flags.add(BindingFlag.TYPE_ONLY);
    }
    builder.declareImport(
        NodeFlowSyntax.text(binding.local()),
        binding.local().span(),
        new ImportOrigin(binding.specifier(), binding.member(), binding.site()),
        flags);
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return builtins.of(environments);
  }
}
