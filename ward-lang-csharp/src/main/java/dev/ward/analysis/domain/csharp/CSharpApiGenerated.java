package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The members of a type that no member declaration writes: the {@code Invoke} method of a delegate,
 * the constants of an enum, the constructors the compiler adds and the properties of a record's
 * parameters.
 */
final class CSharpApiGenerated {
  private static final String PRIMARY = "primary_parameters";
  private static final String PARAMETERS = "parameter_list";
  private static final String PUBLIC = "public";

  private final CSharpApiOwner owner;

  CSharpApiGenerated(final CSharpApiOwner owner) {
    this.owner = owner;
  }

  ApiMember invoke(final SyntaxNode declaration, final SyntaxNode shape) {
    final Optional<SyntaxNode> parameters = CSharpSyntax.child(shape, PARAMETERS);
    return new CSharpApiMemberDraft(
        ApiMember.Kind.METHOD,
        "Invoke",
        CSharpApiModifiers.of(PUBLIC),
        owner,
        declaration
    )
        .signature(
            List.of(),
            owner.refs().parameters(parameters),
            owner.refs().type(CSharpSyntax.child(shape, "return_type").orElseThrow())
        )
        .varargs(CSharpApiRefs.isVarargs(parameters))
        .build();
  }

  List<ApiMember> constants(final SyntaxNode shape) {
    final ApiTypeRef self =
        ApiTypeRef.of(CSharpSyntax.text(CSharpSyntax.name(shape).orElse(shape)));
    return CSharpSyntax.child(shape, "enum_body").stream()
        .flatMap(body -> CSharpSyntax.children(body, "enum_member_declaration").stream())
        .map(
            member ->
                new CSharpApiMemberDraft(
                    ApiMember.Kind.FIELD,
                    CSharpSyntax.name(member).map(CSharpSyntax::text).orElse(""),
                    CSharpApiModifiers.of(PUBLIC),
                    owner,
                    member
                )
                    .signature(List.of(), List.<ApiParameter> of(), self)
                    .constant()
                    .build()
        )
        .toList();
  }

  /** Returns the constructor of the primary parameters, the default constructor and the properties. */
  List<ApiMember> synthesized(
      final SyntaxNode declaration,
      final SyntaxNode shape,
      final CSharpApiModifiers mods,
      final List<ApiMember> declared
  ) {
    final List<ApiMember> added = new ArrayList<>();
    final boolean primary = CSharpSyntax.child(shape, PRIMARY).isPresent();
    final Optional<SyntaxNode> parameters =
        CSharpSyntax.child(shape, PRIMARY).flatMap(list -> CSharpSyntax.child(list, PARAMETERS));
    if (primary) {
      added.add(constructor(declaration, parameters, CSharpApiModifiers.of(PUBLIC)));
    }
    if ("record_declaration".equals(shape.kind())) {
      added.addAll(recordProperties(declaration, parameters));
    }
    final boolean hasConstructor =
        declared.stream().anyMatch(member -> member.kind() == ApiMember.Kind.CONSTRUCTOR);
    if (!owner.isInterface()
        && !mods.has("static")
        && !hasConstructor
        && (!primary || owner.isStruct())) {
      added.add(
          constructor(
              declaration,
              Optional.empty(),
              CSharpApiModifiers.of(mods.has("abstract") ? "protected" : PUBLIC)
          )
      );
    }
    return added;
  }

  private ApiMember constructor(
      final SyntaxNode declaration,
      final Optional<SyntaxNode> parameters,
      final CSharpApiModifiers mods
  ) {
    return new CSharpApiMemberDraft(
        ApiMember.Kind.CONSTRUCTOR,
        ApiMember.CONSTRUCTOR,
        mods,
        owner,
        declaration
    )
        .signature(List.of(), owner.refs().parameters(parameters), CSharpApiRefs.voidType())
        .varargs(CSharpApiRefs.isVarargs(parameters))
        .build();
  }

  private List<ApiMember> recordProperties(
      final SyntaxNode declaration,
      final Optional<SyntaxNode> parameters
  ) {
    return parameters.map(list -> CSharpSyntax.children(list, "parameter")).orElse(List.of())
        .stream()
        .map(
            parameter ->
                new CSharpApiMemberDraft(
                    ApiMember.Kind.METHOD,
                    "get_" + CSharpSyntax.name(parameter).map(CSharpSyntax::text).orElse(""),
                    CSharpApiModifiers.of(PUBLIC),
                    owner,
                    declaration
                )
                    .signature(
                        List.of(),
                        List.of(),
                        owner.refs().type(CSharpSyntax.child(parameter, "type_").orElse(parameter))
                    )
                    .build()
        )
        .toList();
  }
}
