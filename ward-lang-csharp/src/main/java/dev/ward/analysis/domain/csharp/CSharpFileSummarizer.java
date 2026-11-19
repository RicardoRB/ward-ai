package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Summarizes a C# file by its namespaces: the namespaces it declares are its exports, and each
 * {@code using} of a namespace is an import. An alias is not an import, because it names a type.
 */
public final class CSharpFileSummarizer implements FileSummarizer {
  /** The kind of a declared namespace in {@link FileSummary#exports()}. */
  public static final String NAMESPACE_KIND = "namespace";

  private static final String NAMESPACE = "namespace_declaration";
  private static final String FILE_SCOPED = "file_scoped_namespace_declaration";
  private static final String USING = "using_directive";
  private static final String QUALIFIED = "qualified_identifier";
  private static final String ALIAS = "=";
  private static final String TYPE_ARGUMENTS = "<";

  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    final List<Symbol> namespaces = new ArrayList<>();
    final List<Reference> usings = new ArrayList<>();
    walk(tree, "", namespaces, usings);
    return new FileSummary(unit.path(), namespaces, usings, namespaces);
  }

  private static void walk(
      final SyntaxNode node,
      final String scope,
      final List<Symbol> namespaces,
      final List<Reference> usings
  ) {
    if (USING.equals(node.kind())) {
      using(node, usings);
      return;
    }
    String inner = scope;
    if (NAMESPACE.equals(node.kind()) || FILE_SCOPED.equals(node.kind())) {
      inner = declare(node, scope, namespaces);
    }
    for (final SyntaxNode child : node.children()) {
      walk(child, inner, namespaces, usings);
    }
  }

  /** Records the namespace and returns the scope its members are in. */
  private static String declare(
      final SyntaxNode node,
      final String scope,
      final List<Symbol> namespaces
  ) {
    final String name =
        CSharpSyntax.child(node, QUALIFIED)
            .map(CSharpSyntax::text)
            .map(declared -> scope.isEmpty() ? declared : scope + "." + declared)
            .orElse(scope);
    if (!name.equals(scope)) {
      namespaces.add(new Symbol(name, node.span(), NAMESPACE_KIND));
    }
    return name;
  }

  private static void using(final SyntaxNode node, final List<Reference> usings) {
    if (CSharpSyntax.hasToken(node, ALIAS)) {
      return;
    }
    CSharpSyntax.child(node, "namespace_or_type_name")
        .map(CSharpSyntax::text)
        .filter(name -> !name.contains(TYPE_ARGUMENTS))
        .ifPresent(name -> usings.add(new Reference(name, node.span())));
  }
}
