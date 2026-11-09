package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each import declaration whose bindings are all used only as types, without {@code import
 * type}, like typescript-eslint's consistent-type-imports. A type position is a reference inside a
 * type annotation, type arguments or type parameters in the tree.
 */
public final class NodeTypeOnlyImportCheck implements RuleCheck {

  private static final String IMPORT = "importStatement";
  private static final Set<String> TYPE_KINDS =
      Set.of("type_", "typeAnnotation", "typeArguments", "typeParameters", "implementsClause");

  private final String message;
  private final String partialMessage;

  /**
   * Creates the check.
   *
   * @param message the message of a declaration whose bindings are all type-only
   * @param partialMessage the message of a mixed declaration; {@code {names}} lists the type-only
   *     names
   */
  public NodeTypeOnlyImportCheck(final String message, final String partialMessage) {
    this.message = message;
    this.partialMessage = partialMessage;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the consistent-type-imports check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final Map<Span, List<Use>> statements = new LinkedHashMap<>();
    for (final Scope scope : scopes.scopes()) {
      for (final Binding binding : scope.bindings()) {
        if (binding.kind() == BindingKind.IMPORT
            && !binding.flags().contains(BindingFlag.TYPE_ONLY)) {
          record(root, scopes, binding, statements);
        }
      }
    }
    final List<RuleMatch> found = new ArrayList<>();
    statements.forEach((statement, uses) -> report(statement, uses, found));
    return List.copyOf(found);
  }

  /** Adds the binding's use to the import declaration it belongs to. */
  private static void record(
      final SyntaxNode root,
      final UnitScopes scopes,
      final Binding binding,
      final Map<Span, List<Use>> statements) {
    importStatement(root, binding.declaration().startOffset())
        .ifPresent(
            statement ->
                statements
                    .computeIfAbsent(statement, key -> new ArrayList<>())
                    .add(new Use(binding.name(), typeOnly(root, binding, scopes))));
  }

  /**
   * One finding per declaration: the whole-declaration message, or the mixed one with its names.
   */
  private void report(final Span statement, final List<Use> uses, final List<RuleMatch> found) {
    final List<String> typeNames = uses.stream().filter(Use::typeOnly).map(Use::name).toList();
    if (typeNames.isEmpty()) {
      return;
    }
    if (typeNames.size() == uses.size()) {
      found.add(new RuleMatch(statement, message));
    } else {
      found.add(
          new RuleMatch(
              statement, partialMessage.replace("{names}", TypeOnlyNames.quoted(typeNames))));
    }
  }

  /** A binding of an import declaration and whether all its uses are in type positions. */
  private record Use(String name, boolean typeOnly) {}

  /** Whether the binding has uses and each of them is in a type position. */
  private static boolean typeOnly(
      final SyntaxNode root, final Binding binding, final UnitScopes scopes) {
    final List<NameReference> uses =
        scopes.usesOf(binding).stream()
            .filter(use -> use.access() != ReferenceAccess.WRITE)
            .toList();
    return !uses.isEmpty()
        && uses.stream().allMatch(use -> inTypePosition(root, use.span().startOffset()));
  }

  private static boolean inTypePosition(final SyntaxNode root, final int offset) {
    return typeAt(root, offset, false).orElse(false);
  }

  /** Whether the leaf at {@code offset} sits under a type node; empty when no leaf is there. */
  private static Optional<Boolean> typeAt(
      final SyntaxNode node, final int offset, final boolean inType) {
    if (!holds(node.span(), offset)) {
      return Optional.empty();
    }
    final boolean type = inType || TYPE_KINDS.contains(node.kind());
    if (node.children().isEmpty()) {
      return Optional.of(type);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<Boolean> found = typeAt(child, offset, type);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** The span of the import statement that declares the binding at {@code offset}, if any. */
  private static Optional<Span> importStatement(final SyntaxNode node, final int offset) {
    if (IMPORT.equals(node.kind()) && holds(node.span(), offset)) {
      return Optional.of(node.span());
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<Span> found = importStatement(child, offset);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static boolean holds(final Span span, final int offset) {
    return span.startOffset() <= offset && offset < span.endOffset();
  }
}
