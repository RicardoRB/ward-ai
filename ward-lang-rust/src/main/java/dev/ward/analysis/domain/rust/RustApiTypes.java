package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Reads the structs, unions, enums and traits of one Rust file as API types. */
final class RustApiTypes {
  private static final String VISIBILITY = "visibility";
  private static final String GENERICS = "genericParams";
  private static final String TYPE = "type_";
  private static final String FIELDS = "structFields";
  private static final String FIELD = "structField";
  private static final String TUPLE_FIELDS = "tupleFields";
  private static final String TUPLE_FIELD = "tupleField";
  private static final String BOUNDS = "typeParamBounds";
  private static final String BOUND = "typeParamBound";
  private static final String ASSOCIATED = "associatedItem";

  private final String path;
  private final RustApiTypeRefs refs;

  RustApiTypes(final String path, final RustApiTypeRefs refs) {
    this.path = path;
    this.refs = refs;
  }

  /** What a declaration is: its kind, whether it is {@code pub} and the module it is in. */
  record Declared(ApiType.Kind kind, boolean pub, String module) {}

  /** Returns the struct of a {@code struct_} node. */
  RustApiDecls.Item struct(final SyntaxNode node, final Declared declared) {
    final SyntaxNode body = node.children().getFirst();
    final SyntaxNode generics = RustTrees.child(body, GENERICS);
    final RustApiMembers members = members(generics);
    final List<ApiMember> fields = new ArrayList<>();
    fields.addAll(namedFields(RustTrees.child(body, FIELDS), members));
    fields.addAll(tupleFields(RustTrees.child(body, TUPLE_FIELDS), members));
    return type(body, declared, generics, fields, List.of());
  }

  /** Returns the union of a {@code union_} node. */
  RustApiDecls.Item union(final SyntaxNode node, final Declared declared) {
    final SyntaxNode generics = RustTrees.child(node, GENERICS);
    return type(
        node,
        declared,
        generics,
        namedFields(RustTrees.child(node, FIELDS), members(generics)),
        List.of()
    );
  }

  /** Returns the enum of an {@code enumeration} node; its variants are static fields. */
  RustApiDecls.Item enumeration(final SyntaxNode node, final Declared declared) {
    final SyntaxNode generics = RustTrees.child(node, GENERICS);
    final RustApiMembers members = members(generics);
    final SyntaxNode variants = RustTrees.child(node, "enumItems");
    final List<ApiMember> found =
        variants == null
            ? List.of()
            : RustTrees.children(variants, "enumItem").stream().map(members::variant).toList();
    return type(node, declared, generics, found, List.of());
  }

  /** Returns the trait of a {@code trait_} node; a method without a body is abstract. */
  RustApiDecls.Item trait(final SyntaxNode node, final Declared declared) {
    final SyntaxNode generics = RustTrees.child(node, GENERICS);
    final RustApiMembers members = members(generics);
    final List<ApiMember> functions =
        RustTrees.children(node, ASSOCIATED).stream()
            .map(item -> RustTrees.child(item, RustKinds.FUNCTION))
            .filter(Objects::nonNull)
            .map(function -> members.function(function, true, true))
            .toList();
    return type(node, declared, generics, functions, supertypes(node));
  }

  private List<ApiTypeRef> supertypes(final SyntaxNode trait) {
    final SyntaxNode bounds = RustTrees.child(trait, BOUNDS);
    return bounds == null
        ? List.of()
        : RustTrees.children(bounds, BOUND).stream().map(refs::of).toList();
  }

  private RustApiMembers members(final SyntaxNode generics) {
    return new RustApiMembers(path, refs.with(RustApiGenerics.variables(generics)));
  }

  private List<ApiMember> namedFields(final SyntaxNode fields, final RustApiMembers members) {
    return fields == null
        ? List.of()
        : RustTrees.children(fields, FIELD).stream()
            .filter(field -> RustApiMembers.isPub(RustTrees.child(field, VISIBILITY)))
            .map(field -> named(field, members))
            .toList();
  }

  private static ApiMember named(final SyntaxNode field, final RustApiMembers members) {
    final SyntaxNode name = RustTrees.child(field, RustKinds.IDENTIFIER);
    return members.field(RustTrees.name(name), RustTrees.child(field, TYPE), true, false, name);
  }

  private List<ApiMember> tupleFields(final SyntaxNode fields, final RustApiMembers members) {
    if (fields == null) {
      return List.of();
    }
    final List<SyntaxNode> all = RustTrees.children(fields, TUPLE_FIELD);
    return IntStream.range(0, all.size())
        .filter(index -> RustApiMembers.isPub(RustTrees.child(all.get(index), VISIBILITY)))
        .mapToObj(
            index ->
                members.field(
                    Integer.toString(index),
                    RustTrees.child(all.get(index), TYPE),
                    true,
                    false,
                    all.get(index)
                )
        )
        .toList();
  }

  private RustApiDecls.Item type(
      final SyntaxNode node,
      final Declared declared,
      final SyntaxNode generics,
      final List<ApiMember> found,
      final List<ApiTypeRef> supertypes
  ) {
    final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
    return new RustApiDecls.Item(
        declared.module(),
        declared.pub(),
        new ApiType(
            RustTrees.name(name),
            declared.kind(),
            declared.pub() ? ApiVisibility.PUBLIC : ApiVisibility.PACKAGE,
            declared.kind() != ApiType.Kind.INTERFACE,
            false,
            false,
            new RustApiGenerics(refs).parameters(generics),
            supertypes,
            found,
            new RustApiMembers(path, refs).at(name)
        )
    );
  }
}
