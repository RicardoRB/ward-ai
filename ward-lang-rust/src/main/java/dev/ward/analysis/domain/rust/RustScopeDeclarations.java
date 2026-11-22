package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The names items declare: functions, types, constants, modules and imports, hoisted to the top of
 * their module or block. An item with a visibility is exported.
 */
final class RustScopeDeclarations {
  private static final Map<String, BindingKind> NAMED =
      Map.of(
          RustKinds.FUNCTION,
          BindingKind.FUNCTION,
          "enumeration",
          BindingKind.CLASS,
          "union_",
          BindingKind.CLASS,
          "trait_",
          BindingKind.CLASS,
          "typeAlias",
          BindingKind.TYPE,
          "constantItem",
          BindingKind.CONSTANT,
          "staticItem",
          BindingKind.VARIABLE,
          "module",
          BindingKind.TYPE
      );

  private final RustScopeWalk walk;
  private final RustScopeImports imports;

  RustScopeDeclarations(final RustScopeWalk walk) {
    this.walk = walk;
    this.imports = new RustScopeImports(walk);
  }

  /** Declares what each of {@code items} (nodes of kind {@code item}) names. */
  void declareAll(final List<SyntaxNode> items) {
    items.forEach(this::declareItem);
  }

  private void declareItem(final SyntaxNode item) {
    final SyntaxNode visItem = RustTrees.child(item, RustKinds.VIS_ITEM);
    if (visItem == null) {
      return;
    }
    final Set<BindingFlag> flags = flagsOf(visItem);
    visItem.children().forEach(inner -> declareInner(inner, flags));
  }

  private static Set<BindingFlag> flagsOf(final SyntaxNode visItem) {
    final Set<BindingFlag> flags = EnumSet.of(BindingFlag.HOISTED);
    if (RustTrees.child(visItem, "visibility") != null) {
      flags.add(BindingFlag.EXPORTED);
    }
    return flags;
  }

  private void declareInner(final SyntaxNode inner, final Set<BindingFlag> flags) {
    final String kind = inner.kind();
    if (NAMED.containsKey(kind)) {
      declareNamed(inner, NAMED.get(kind), flags);
    } else if (RustKinds.STRUCT.equals(kind)) {
      inner.children().forEach(struct -> declareNamed(struct, BindingKind.CLASS, flags));
    } else if (RustKinds.USE_DECLARATION.equals(kind)) {
      imports.useTree(RustTrees.child(inner, "useTree"), List.of(), flags);
    } else if (RustKinds.EXTERN_CRATE.equals(kind)) {
      externCrate(inner);
    } else if (RustKinds.EXTERN_BLOCK.equals(kind)) {
      RustTrees.children(inner, "externalItem")
          .forEach(item -> item.children().forEach(each -> declareInner(each, flags)));
    }
  }

  private void declareNamed(
      final SyntaxNode item,
      final BindingKind kind,
      final Set<BindingFlag> flags
  ) {
    final SyntaxNode name = RustTrees.child(item, RustKinds.IDENTIFIER);
    if (name != null) {
      walk.builder.declare(RustTrees.name(name), kind, name.span(), flags);
    }
  }

  private void externCrate(final SyntaxNode node) {
    final SyntaxNode alias = RustTrees.child(node, "asClause");
    final SyntaxNode crate = RustTrees.child(node, "crateRef");
    final SyntaxNode name =
        alias == null
            ? RustTrees.find(crate, RustKinds.IDENTIFIER)
            : RustTrees.child(alias, RustKinds.IDENTIFIER);
    if (name != null) {
      walk.builder.declare(
          RustTrees.name(name),
          BindingKind.IMPORT,
          name.span(),
          EnumSet.of(BindingFlag.HOISTED)
      );
    }
  }
}
