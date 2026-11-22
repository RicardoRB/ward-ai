package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the functions, fields, constants and variants of one Rust file as API members. */
final class RustApiMembers {
  private static final String FUNCTION_PARAMS = "functionParameters";
  private static final String FUNCTION_PARAM = "functionParam";
  private static final String SELF_PARAM = "selfParam";
  private static final String TYPE = "type_";
  private static final String RETURN = "functionReturnType";
  private static final String GENERICS = "genericParams";
  private static final String QUALIFIERS = "functionQualifiers";
  private static final String PUB = "pub";
  private static final String UNIT = "()";
  private static final List<String> SIGNATURE_QUALIFIERS = List.of("async", "unsafe");

  private final String path;
  private final RustApiTypeRefs refs;

  RustApiMembers(final String path, final RustApiTypeRefs refs) {
    this.path = path;
    this.refs = refs;
  }

  /** Returns the function of a {@code function_} node. */
  ApiMember function(final SyntaxNode node, final boolean pub, final boolean inTrait) {
    final RustApiMembers own = new RustApiMembers(
        path,
        refs.with(RustApiGenerics.variables(RustTrees.child(node, GENERICS)))
    );
    final SyntaxNode parameters = RustTrees.child(node, FUNCTION_PARAMS);
    final boolean method = parameters != null && RustTrees.child(parameters, SELF_PARAM) != null;
    final boolean hasBody = RustTrees.child(node, RustKinds.BLOCK) != null;
    return new ApiMember(
        ApiMember.Kind.METHOD,
        RustTrees.name(RustTrees.child(node, RustKinds.IDENTIFIER)),
        visibility(pub),
        !method,
        false,
        inTrait && !hasBody,
        inTrait && hasBody,
        false,
        new RustApiGenerics(refs).parameters(RustTrees.child(node, GENERICS)),
        own.parameters(parameters),
        own.returned(node),
        at(RustTrees.child(node, RustKinds.IDENTIFIER))
    );
  }

  private List<ApiParameter> parameters(final SyntaxNode parameters) {
    return parameters == null
        ? List.of()
        : RustTrees.children(parameters, FUNCTION_PARAM).stream()
            .map(param -> new ApiParameter(refs.of(RustTrees.find(param, TYPE)), false))
            .toList();
  }

  private ApiTypeRef returned(final SyntaxNode function) {
    final SyntaxNode result = RustTrees.child(function, RETURN);
    final String text = result == null ? UNIT : refs.text(RustTrees.child(result, TYPE));
    final SyntaxNode qualifiers = RustTrees.child(function, QUALIFIERS);
    final String prefix =
        qualifiers == null
            ? ""
            : SIGNATURE_QUALIFIERS.stream()
                .filter(word -> RustTrees.leaf(qualifiers, "'" + word + "'") != null)
                .reduce("", (joined, word) -> joined + word + " ");
    final ApiTypeRef plain = refs.ref(text);
    return new ApiTypeRef(plain.text(), prefix + plain.key(), plain.erasure(), false);
  }

  /** Returns a field called {@code name} of the type written in {@code type}. */
  ApiMember field(
      final String name,
      final SyntaxNode type,
      final boolean pub,
      final boolean isStatic,
      final SyntaxNode where
  ) {
    return value(name, refs.of(type), pub, isStatic, where);
  }

  /** Returns an enum variant as a static field whose type is its payload. */
  ApiMember variant(final SyntaxNode item) {
    final SyntaxNode name = RustTrees.child(item, RustKinds.IDENTIFIER);
    final SyntaxNode payload =
        item.children().stream()
            .filter(child -> child.kind().startsWith("enumItem"))
            .findFirst()
            .orElse(null);
    return value(
        RustTrees.name(name),
        refs.ref(payload == null ? UNIT : refs.text(payload)),
        true,
        true,
        name
    );
  }

  private ApiMember value(
      final String name,
      final ApiTypeRef type,
      final boolean pub,
      final boolean isStatic,
      final SyntaxNode where
  ) {
    return new ApiMember(
        ApiMember.Kind.FIELD,
        name,
        visibility(pub),
        isStatic,
        false,
        false,
        false,
        false,
        List.of(),
        List.of(),
        type,
        at(where)
    );
  }

  /** Returns the member under another name. */
  static ApiMember renamed(final ApiMember member, final String name) {
    return new ApiMember(
        member.kind(),
        name,
        member.visibility(),
        member.isStatic(),
        member.isFinal(),
        member.isAbstract(),
        member.isDefault(),
        member.isVarargs(),
        member.typeParameters(),
        member.parameters(),
        member.type(),
        member.location()
    );
  }

  ApiLocation at(final SyntaxNode node) {
    return new ApiLocation(path, node.span().startLine(), node.span().startColumn());
  }

  /** Returns whether a {@code visibility} node is a plain {@code pub}. */
  static boolean isPub(final SyntaxNode visibility) {
    return visibility != null && PUB.equals(RustTrees.text(visibility));
  }

  private static ApiVisibility visibility(final boolean pub) {
    return pub ? ApiVisibility.PUBLIC : ApiVisibility.PACKAGE;
  }
}
