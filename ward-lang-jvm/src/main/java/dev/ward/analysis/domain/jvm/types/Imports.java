package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The package and imports of one Java file, as qualified names with dots. Static imports are kept
 * apart, because they name members, not types.
 *
 * @param packageName the declared package, empty for the default package
 * @param single the single-type imports: simple name to qualified name
 * @param onDemand the packages or types imported with {@code .*}
 * @param staticSingle the single static imports: member name to the qualified name of its owner
 * @param staticOnDemand the types imported with {@code import static T.*}
 */
record Imports(
    String packageName,
    Map<String, String> single,
    List<String> onDemand,
    Map<String, List<String>> staticSingle,
    List<String> staticOnDemand) {

  Imports {
    single = Map.copyOf(single);
    onDemand = List.copyOf(onDemand);
    staticSingle = Map.copyOf(staticSingle);
    staticOnDemand = List.copyOf(staticOnDemand);
  }

  /** Returns the imports of a file that declares package {@code packageName} and imports none. */
  static Imports none(final String packageName) {
    return new Imports(packageName, Map.of(), List.of(), Map.of(), List.of());
  }

  /** Reads the package and imports of the compilation unit {@code root}. */
  static Imports of(final SyntaxNode root) {
    String packageName = "";
    final Map<String, String> single = new HashMap<>();
    final List<String> onDemand = new ArrayList<>();
    final Map<String, List<String>> staticSingle = new HashMap<>();
    final List<String> staticOnDemand = new ArrayList<>();
    for (final SyntaxNode child : root.children()) {
      if ("packageDeclaration".equals(child.kind())) {
        packageName = nameOf(child).orElse("");
      } else if ("importDeclaration".equals(child.kind())) {
        final Optional<String> name = nameOf(child);
        final boolean wildcard = Syntax.hasToken(child, "*");
        final boolean isStatic = Syntax.hasToken(child, "static");
        if (name.isEmpty()) {
          continue;
        }
        if (wildcard) {
          (isStatic ? staticOnDemand : onDemand).add(name.get());
        } else if (isStatic) {
          final int dot = name.get().lastIndexOf('.');
          if (dot > 0) {
            staticSingle
                .computeIfAbsent(name.get().substring(dot + 1), key -> new ArrayList<>())
                .add(name.get().substring(0, dot));
          }
        } else {
          single.put(name.get().substring(name.get().lastIndexOf('.') + 1), name.get());
        }
      }
    }
    return new Imports(packageName, single, onDemand, staticSingle, staticOnDemand);
  }

  private static Optional<String> nameOf(final SyntaxNode declaration) {
    return Syntax.child(declaration, "qualifiedName").map(Syntax::text);
  }
}
