package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiSurfaceReader;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads the public API of C# source files: the types with their accessibility, the members that
 * code outside the assembly can use, and the accessors of properties and events as methods. It
 * reads names as written, because no semantic model resolves them.
 */
public final class CSharpApiSurfaceReader implements ApiSurfaceReader {
  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.CSHARP;
  }

  @Override
  public List<ApiType> read(
      final List<SourceUnit> units,
      final LanguageFrontend frontend,
      final Predicate<String> libraryExists
  ) {
    final List<ApiType> found = new ArrayList<>();
    for (final SourceUnit unit : units) {
      final ParseResult result = frontend.parse(unit);
      if (!result.diagnostics().isEmpty()) {
        final AnalysisFinding first = result.diagnostics().getFirst();
        throw new ApiSurfaceException(
            unit.path(),
            "syntax error" + (first.line() == null ? "" : " at line " + first.line())
        );
      }
      declare(unit.path(), result.tree(), found);
    }
    found.sort(Comparator.comparing(ApiType::name));
    return List.copyOf(found);
  }

  private static void declare(final String path, final SyntaxNode tree, final List<ApiType> found) {
    CSharpSyntax.child(tree, "compilation_unit")
        .ifPresent(root -> members(root, "", new CSharpApiTypeReader(path, found)));
  }

  /** Reads the declarations of a compilation unit or a namespace body. */
  private static void members(
      final SyntaxNode container,
      final String namespace,
      final CSharpApiTypeReader types
  ) {
    String current = namespace;
    for (final SyntaxNode member : CSharpSyntax.children(
        container,
        "namespace_member_declaration"
    )) {
      for (final SyntaxNode declaration : member.children()) {
        current = declare(declaration, namespace, current, types);
      }
    }
  }

  /** Reads one declaration; returns the namespace the following declarations belong to. */
  private static String declare(
      final SyntaxNode declaration,
      final String outer,
      final String current,
      final CSharpApiTypeReader types
  ) {
    switch (declaration.kind()) {
      case "namespace_declaration" -> {
        CSharpSyntax.child(declaration, "namespace_body")
            .ifPresent(body -> members(body, join(current, nameOf(declaration)), types));
        return current;
      }
      case "file_scoped_namespace_declaration" -> {
        return join(outer, nameOf(declaration));
      }
      case "type_declaration" -> {
        types.declare(declaration, current, Map.of(), false);
        return current;
      }
      default -> {
        return current;
      }
    }
  }

  private static String nameOf(final SyntaxNode declaration) {
    return CSharpSyntax.child(declaration, "qualified_identifier")
        .map(CSharpSyntax::text)
        .orElse("");
  }

  private static String join(final String prefix, final String name) {
    return prefix.isEmpty() ? name : prefix + "." + name;
  }
}
