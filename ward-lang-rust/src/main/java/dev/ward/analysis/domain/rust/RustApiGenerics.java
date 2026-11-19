package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Objects;

/** Reads the type parameters of a {@code genericParams} node; lifetimes and constants are left out. */
final class RustApiGenerics {
  private static final String GENERIC = "genericParam";
  private static final String TYPE_PARAM = "typeParam";
  private static final String BOUNDS = "typeParamBounds";
  private static final String BOUND = "typeParamBound";

  private final RustApiTypeRefs refs;

  RustApiGenerics(final RustApiTypeRefs refs) {
    this.refs = refs;
  }

  /** Returns the names of the type parameters of {@code generics}, or none when it is null. */
  static List<String> variables(final SyntaxNode generics) {
    return typeParams(generics).stream()
        .map(param -> RustTrees.name(RustTrees.child(param, RustKinds.IDENTIFIER)))
        .toList();
  }

  private static List<SyntaxNode> typeParams(final SyntaxNode generics) {
    return generics == null
        ? List.of()
        : RustTrees.children(generics, GENERIC).stream()
            .map(param -> RustTrees.child(param, TYPE_PARAM))
            .filter(Objects::nonNull)
            .toList();
  }

  /** Returns the type parameters of {@code generics} with their bounds. */
  List<ApiTypeParameter> parameters(final SyntaxNode generics) {
    return typeParams(generics).stream().map(this::parameter).toList();
  }

  private ApiTypeParameter parameter(final SyntaxNode param) {
    final SyntaxNode bounds = RustTrees.child(param, BOUNDS);
    final List<ApiTypeRef> found =
        bounds == null
            ? List.of()
            : RustTrees.children(bounds, BOUND).stream().map(refs::of).toList();
    return new ApiTypeParameter(
        RustTrees.name(RustTrees.child(param, RustKinds.IDENTIFIER)),
        found,
        ""
    );
  }
}
