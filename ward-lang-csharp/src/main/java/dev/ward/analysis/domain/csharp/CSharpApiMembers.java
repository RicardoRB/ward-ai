package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Reads methods, constructors, fields and constants of a C# type. */
final class CSharpApiMembers {
  private static final String TYPE = "type_";
  private static final String PARAMETERS = "parameter_list";

  private final CSharpApiOwner owner;
  private final CSharpApiAccessors accessors;

  CSharpApiMembers(final CSharpApiOwner owner) {
    this.owner = owner;
    this.accessors = new CSharpApiAccessors(owner);
  }

  /** Returns the API members {@code declaration}, a {@code member_declaration}, declares. */
  List<ApiMember> read(final SyntaxNode declaration, final SyntaxNode body) {
    final CSharpApiModifiers mods = new CSharpApiModifiers(declaration);
    return switch (body.kind()) {
      case "method_declaration" -> method(declaration, body, mods);
      case "constructor_declaration" -> constructor(declaration, body, mods);
      case "field_declaration" -> fields(declaration, body, mods, false);
      case "constant_declaration" -> fields(declaration, body, mods, true);
      default -> accessors.read(declaration, body, mods);
    };
  }

  private List<ApiMember> method(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods
  ) {
    final SyntaxNode name = CSharpSyntax.child(body, "member_name").orElseThrow();
    if (CSharpSyntax.isQualified(name)) {
      return List.of(); // an explicit interface implementation is reached through the interface
    }
    final List<String> typeNames = typeNames(body, name);
    final CSharpApiRefs refs = owner.refs().withMethodVariables(typeNames);
    final Optional<SyntaxNode> parameters = CSharpSyntax.child(body, PARAMETERS);
    final SyntaxNode returned = CSharpSyntax.child(body, "return_type").orElseThrow();
    final boolean bodyless =
        CSharpSyntax.child(body, "method_body").map(CSharpApiMembers::isSemicolon).orElse(true);
    return List.of(
        new CSharpApiMemberDraft(
            ApiMember.Kind.METHOD,
            CSharpSyntax.name(name).map(CSharpSyntax::text).orElse(""),
            mods,
            owner,
            declaration
        )
            .signature(
                refs.typeParameters(
                    typeNames,
                    CSharpSyntax.children(body, "type_parameter_constraints_clause")
                ),
                refs.parameters(parameters),
                refs.type(returned)
            )
            .varargs(CSharpApiRefs.isVarargs(parameters))
            .bodyless(bodyless)
            .build()
    );
  }

  /** The grammar reads {@code M<T>} as a member name with type arguments, so look there too. */
  private static List<String> typeNames(final SyntaxNode body, final SyntaxNode name) {
    final List<String> declared =
        CSharpApiRefs.names(CSharpSyntax.child(body, "type_parameter_list"));
    if (!declared.isEmpty()) {
      return declared;
    }
    return CSharpSyntax.child(name, "type_argument_list").stream()
        .flatMap(list -> CSharpSyntax.children(list, "type_argument").stream())
        .map(CSharpSyntax::text)
        .toList();
  }

  private static boolean isSemicolon(final SyntaxNode methodBody) {
    return ";".equals(CSharpSyntax.text(methodBody));
  }

  private List<ApiMember> constructor(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods
  ) {
    if (mods.has("static")) {
      return List.of();
    }
    final Optional<SyntaxNode> parameters = CSharpSyntax.child(body, PARAMETERS);
    return List.of(
        new CSharpApiMemberDraft(
            ApiMember.Kind.CONSTRUCTOR,
            ApiMember.CONSTRUCTOR,
            mods,
            owner,
            declaration
        )
            .signature(List.of(), owner.refs().parameters(parameters), CSharpApiRefs.VOID)
            .varargs(CSharpApiRefs.isVarargs(parameters))
            .build()
    );
  }

  private List<ApiMember> fields(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods,
      final boolean constant
  ) {
    final ApiTypeRef type = owner.refs().type(CSharpSyntax.child(body, TYPE).orElseThrow());
    return CSharpApiAccessors.declaredNames(body).stream()
        .map(
            name -> {
              final CSharpApiMemberDraft draft =
                  new CSharpApiMemberDraft(ApiMember.Kind.FIELD, name, mods, owner, declaration)
                      .signature(List.of(), List.of(), type);
              return (constant ? draft.constant() : draft).build();
            }
        )
        .toList();
  }
}
