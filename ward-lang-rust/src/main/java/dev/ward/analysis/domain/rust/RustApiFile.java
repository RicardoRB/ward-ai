package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads what one Rust file declares: its types, functions, impls, modules and imports. */
final class RustApiFile {
  private static final String VISIBILITY = "visibility";
  private static final String GENERICS = "genericParams";
  private static final String TYPE = "type_";
  private static final String ASSOCIATED = "associatedItem";
  private static final String CONSTANT = "constantItem";
  private static final String INHERENT = "inherentImpl";
  private static final String SEPARATOR = "::";

  private final SourceUnit unit;
  private final RustApiTypeRefs refs;
  private final RustApiTypes types;
  private final Map<String, Handler> handlers;

  RustApiFile(final SourceUnit unit) {
    this.unit = unit;
    this.refs = new RustApiTypeRefs(unit.text(), List.of());
    this.types = new RustApiTypes(unit.path(), refs);
    this.handlers =
        Map.of(
            "module",
            this::module,
            "struct_",
            (node, pub, module, decls) -> decls.item(
                types.struct(node, declared(ApiType.Kind.CLASS, pub, module))
            ),
            "union_",
            (node, pub, module, decls) -> decls.item(
                types.union(node, declared(ApiType.Kind.CLASS, pub, module))
            ),
            "enumeration",
            (node, pub, module, decls) -> decls.item(
                types.enumeration(node, declared(ApiType.Kind.ENUM, pub, module))
            ),
            "trait_",
            (node, pub, module, decls) -> decls.item(
                types.trait(node, declared(ApiType.Kind.INTERFACE, pub, module))
            ),
            "function_",
            this::function,
            CONSTANT,
            this::constant,
            "staticItem",
            this::constant,
            "implementation",
            (node, pub, module, decls) -> implementation(node, module, decls),
            "useDeclaration",
            this::uses
        );
  }

  /** Handles one declaration of a module. */
  @FunctionalInterface
  private interface Handler {
    void read(SyntaxNode node, boolean pub, String module, RustApiDecls.Builder decls);
  }

  /** Reads the crate or module tree of this file, declared as the module {@code module}. */
  RustApiDecls read(final SyntaxNode tree, final String module) {
    final RustApiDecls.Builder decls = new RustApiDecls.Builder();
    items(tree, module, decls);
    return decls.build();
  }

  private static RustApiTypes.Declared declared(
      final ApiType.Kind kind,
      final boolean pub,
      final String module
  ) {
    return new RustApiTypes.Declared(kind, pub, module);
  }

  private void items(
      final SyntaxNode parent,
      final String module,
      final RustApiDecls.Builder decls
  ) {
    RustTrees.children(parent, RustKinds.ITEM).stream()
        .map(item -> RustTrees.child(item, RustKinds.VIS_ITEM))
        .filter(Objects::nonNull)
        .forEach(visItem -> declaration(visItem, module, decls));
  }

  private void declaration(
      final SyntaxNode visItem,
      final String module,
      final RustApiDecls.Builder decls
  ) {
    final boolean pub = RustApiMembers.isPub(RustTrees.child(visItem, VISIBILITY));
    visItem.children().stream()
        .filter(node -> handlers.containsKey(node.kind()))
        .forEach(node -> handlers.get(node.kind()).read(node, pub, module, decls));
  }

  private void function(
      final SyntaxNode node,
      final boolean pub,
      final String module,
      final RustApiDecls.Builder decls
  ) {
    decls.top(
        new RustApiDecls.Top(
            module,
            pub,
            new RustApiMembers(unit.path(), refs).function(node, pub, false)
        )
    );
  }

  private void module(
      final SyntaxNode node,
      final boolean pub,
      final String parent,
      final RustApiDecls.Builder decls
  ) {
    final String path = parent + "." + RustTrees.name(RustTrees.child(node, RustKinds.IDENTIFIER));
    decls.module(new RustApiDecls.Module(path, pub));
    items(node, path, decls);
  }

  private void uses(
      final SyntaxNode node,
      final boolean pub,
      final String module,
      final RustApiDecls.Builder decls
  ) {
    if (pub) {
      RustApiUses.of(node).forEach(
          imported ->
              decls.use(new RustApiDecls.Use(module, true, imported.path(), imported.alias()))
      );
    }
  }

  private void constant(
      final SyntaxNode node,
      final boolean pub,
      final String module,
      final RustApiDecls.Builder decls
  ) {
    final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
    final SyntaxNode type = RustTrees.child(node, TYPE);
    if (name != null && type != null) {
      decls.top(
          new RustApiDecls.Top(
              module,
              pub,
              new RustApiMembers(unit.path(), refs).field(
                  RustTrees.name(name),
                  type,
                  pub,
                  true,
                  name
              )
          )
      );
    }
  }

  private void implementation(
      final SyntaxNode node,
      final String module,
      final RustApiDecls.Builder decls
  ) {
    final SyntaxNode inherent = RustTrees.child(node, INHERENT);
    if (inherent == null) {
      return;
    }
    final RustApiMembers members =
        new RustApiMembers(
            unit.path(),
            refs.with(RustApiGenerics.variables(RustTrees.child(inherent, GENERICS)))
        );
    final String target = refs.ref(refs.text(RustTrees.child(inherent, TYPE))).erasure();
    final List<ApiMember> found =
        RustTrees.children(inherent, ASSOCIATED).stream()
            .flatMap(item -> associated(item, members).stream())
            .toList();
    decls.impl(
        new RustApiDecls.Impl(module, target.substring(target.lastIndexOf(SEPARATOR) + 1), found)
    );
  }

  private static List<ApiMember> associated(final SyntaxNode item, final RustApiMembers members) {
    final boolean pub = RustApiMembers.isPub(RustTrees.child(item, VISIBILITY));
    final SyntaxNode function = RustTrees.child(item, RustKinds.FUNCTION);
    if (function != null) {
      return List.of(members.function(function, pub, false));
    }
    final SyntaxNode constant = RustTrees.child(item, CONSTANT);
    final SyntaxNode name = constant == null ? null : RustTrees.child(
        constant,
        RustKinds.IDENTIFIER
    );
    final SyntaxNode type = constant == null ? null : RustTrees.child(constant, TYPE);
    return name == null || type == null
        ? List.of()
        : List.of(members.field(RustTrees.name(name), type, pub, true, name));
  }
}
