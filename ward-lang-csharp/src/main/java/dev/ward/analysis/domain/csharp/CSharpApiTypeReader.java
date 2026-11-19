package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the type declarations of one file, with the nested ones, into {@link ApiType}s. A generic
 * type is named like its metadata, {@code Name`n}; a nested type {@code Outer.Inner}.
 */
final class CSharpApiTypeReader {
  private static final String BODY = "type_declaration_body";
  private static final String CLASS_BODY = "class_body";

  private final String path;
  private final List<ApiType> found;

  CSharpApiTypeReader(final String path, final List<ApiType> found) {
    this.path = path;
    this.found = found;
  }

  /**
   * Reads {@code declaration}, a {@code type_declaration} or a {@code member_declaration} of a
   * type, and the types nested in it.
   *
   * @param prefix the qualified name of the namespace or the enclosing type
   * @param outer the type variables of the enclosing types, by name to position key
   */
  void declare(
      final SyntaxNode declaration,
      final String prefix,
      final Map<String,
      String> outer,
      final boolean nested
  ) {
    final SyntaxNode shape = shape(declaration).orElse(null);
    if (shape == null) {
      return;
    }
    final CSharpApiModifiers mods = new CSharpApiModifiers(declaration);
    final Optional<SyntaxNode> typeList = CSharpSyntax.child(shape, "type_parameter_list");
    final List<String> own = CSharpApiRefs.names(typeList);
    final Map<String, String> variables = new HashMap<>(outer);
    own.forEach(name -> variables.put(name, "!" + variables.size()));
    final CSharpApiRefs refs = new CSharpApiRefs(variables);
    final String name =
        prefix
            + (prefix.isEmpty() ? "" : ".")
            + CSharpSyntax.name(shape).map(CSharpSyntax::text).orElse("")
            + (own.isEmpty() ? "" : "`" + own.size());
    final ApiVisibility visibility =
        mods.visibility(nested ? ApiVisibility.PRIVATE : ApiVisibility.PACKAGE);
    final boolean isInterface = "interface_declaration".equals(shape.kind());
    final boolean isStruct = isStruct(shape);
    final CSharpApiOwner owner = new CSharpApiOwner(path, isInterface, isStruct, refs);
    final List<ApiMember> members = new ArrayList<>(members(declaration, shape, mods, owner));
    final boolean isStatic = mods.has("static");
    found.add(
        new ApiType(
            name,
            kind(shape),
            visibility,
            isStatic || mods.has("sealed") || isStruct || isFinalKind(shape),
            false,
            isStatic || isInterface || mods.has("abstract"),
            refs.typeParameters(
                own,
                CSharpSyntax.children(shape, "type_parameter_constraints_clause")
            ),
            supertypes(shape, refs),
            members,
            new ApiLocation(path, declaration.span().startLine(), declaration.span().startColumn())
        )
    );
    nestedTypes(shape, name, variables);
  }

  private static Optional<SyntaxNode> shape(final SyntaxNode declaration) {
    final Optional<SyntaxNode> direct = CSharpSyntax.child(declaration, BODY);
    if (direct.isPresent()) {
      return direct.flatMap(body -> body.children().stream().findFirst());
    }
    return CSharpSyntax.child(declaration, "member_body")
        .flatMap(body -> CSharpSyntax.child(body, BODY))
        .flatMap(body -> body.children().stream().findFirst());
  }

  private static boolean isStruct(final SyntaxNode shape) {
    return "struct_declaration".equals(shape.kind())
        || "record_declaration".equals(shape.kind()) && CSharpSyntax.hasToken(shape, "struct");
  }

  private static boolean isFinalKind(final SyntaxNode shape) {
    return "enum_declaration".equals(shape.kind()) || "delegate_declaration".equals(shape.kind());
  }

  private static ApiType.Kind kind(final SyntaxNode shape) {
    return switch (shape.kind()) {
      case "interface_declaration" -> ApiType.Kind.INTERFACE;
      case "enum_declaration" -> ApiType.Kind.ENUM;
      case "record_declaration" -> ApiType.Kind.RECORD;
      default -> ApiType.Kind.CLASS;
    };
  }

  private static List<ApiTypeRef> supertypes(final SyntaxNode shape, final CSharpApiRefs refs) {
    return CSharpSyntax.child(shape, "class_base").stream()
        .flatMap(base -> CSharpSyntax.children(base, "base_type").stream())
        .map(base -> CSharpSyntax.child(base, "type_name").orElse(base))
        .map(refs::type)
        .toList();
  }

  private List<ApiMember> members(
      final SyntaxNode declaration,
      final SyntaxNode shape,
      final CSharpApiModifiers mods,
      final CSharpApiOwner owner
  ) {
    final CSharpApiGenerated generated = new CSharpApiGenerated(owner);
    final List<ApiMember> members = new ArrayList<>();
    if ("delegate_declaration".equals(shape.kind())) {
      members.add(generated.invoke(declaration, shape));
      return members;
    }
    if ("enum_declaration".equals(shape.kind())) {
      members.addAll(generated.constants(shape));
      return members;
    }
    final CSharpApiMembers reader = new CSharpApiMembers(owner);
    CSharpSyntax.child(shape, CLASS_BODY).stream()
        .flatMap(body -> CSharpSyntax.children(body, "member_declaration").stream())
        .forEach(
            member ->
                CSharpSyntax.child(member, "member_body")
                    .flatMap(body -> body.children().stream().findFirst())
                    .ifPresent(body -> members.addAll(reader.read(member, body)))
        );
    members.addAll(generated.synthesized(declaration, shape, mods, members));
    return members;
  }

  private void nestedTypes(
      final SyntaxNode shape,
      final String name,
      final Map<String,
      String> variables
  ) {
    CSharpSyntax.child(shape, CLASS_BODY).stream()
        .flatMap(body -> CSharpSyntax.children(body, "member_declaration").stream())
        .forEach(member -> declare(member, name, variables, true));
  }
}
