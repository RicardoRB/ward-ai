package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Reads the types and the top-level declarations of one parsed Kotlin file. */
final class KotlinApiUnitReader {
  private static final String FILE = "kotlinFile";
  private static final String TOP = "topLevelObject";

  private final String path;
  private final SyntaxNode tree;
  private final Predicate<String> exists;

  KotlinApiUnitReader(final String path, final SyntaxNode tree, final Predicate<String> exists) {
    this.path = path;
    this.tree = tree;
    this.exists = exists;
  }

  /** Returns the declarations at the top level of a file. */
  static List<SyntaxNode> topLevelObjects(final SyntaxNode tree) {
    final SyntaxNode file = Syntax.find(tree, FILE).orElse(tree);
    return Syntax.children(file, TOP).stream().map(top -> top.children().getFirst()).toList();
  }

  /** Adds the file's types to {@code found} and its top-level members to {@code topLevel}. */
  void read(final List<ApiType> found, final KotlinApiTopLevel topLevel) {
    final Imports imports = KotlinTypes.imports(tree);
    final Set<String> imported = new HashSet<>(imports.single().values());
    final KotlinApiRefs file =
        new KotlinApiRefs(
            new TypeScope(imports, name -> imported.contains(name) || exists.test(name)),
            new ApiTypeRefs());
    final KotlinApiTypeReader types = new KotlinApiTypeReader(path);
    final KotlinApiMembers members = new KotlinApiMembers(path, file, false);
    final String facade = KotlinApiTopLevel.typeName(imports.packageName());
    topLevelObjects(tree)
        .forEach(
            declaration -> {
              if (KotlinApiTypeReader.isType(declaration)) {
                types.read(
                    declaration,
                    BinaryNames.qualified(
                        imports.packageName(), KotlinApiTypeReader.nameOf(declaration)),
                    file,
                    found);
              } else {
                final List<ApiMember> own = members.of(declaration);
                topLevel.add(facade, own);
              }
            });
  }
}
