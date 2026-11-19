package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the members that compile to methods: the accessors of properties and indexers, the
 * accessors of events and the operators. An accessor is a method named {@code get_X}, {@code
 * set_X} ({@code init} too), {@code add_X} or {@code remove_X}, as in the compiled assembly.
 */
final class CSharpApiAccessors {
  private static final String TYPE = "type_";
  private static final String MEMBER_NAME = "member_name";

  private final CSharpApiOwner owner;

  CSharpApiAccessors(final CSharpApiOwner owner) {
    this.owner = owner;
  }

  /** Returns the declared names of a field, constant or event declaration. */
  static List<String> declaredNames(final SyntaxNode declaration) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode list : declaration.children()) {
      if (!list.kind().endsWith("_declarators")) {
        continue;
      }
      for (final SyntaxNode declarator : list.children()) {
        CSharpSyntax.name(declarator).ifPresent(name -> names.add(CSharpSyntax.text(name)));
      }
    }
    return names;
  }

  List<ApiMember> read(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods
  ) {
    return switch (body.kind()) {
      case "property_declaration" -> property(declaration, body, mods);
      case "indexer_declaration" -> indexer(declaration, body, mods);
      case "event_declaration" -> event(declaration, body, mods);
      case "operator_declaration" -> operator(declaration, body, mods);
      default -> List.of();
    };
  }

  private List<ApiMember> property(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods
  ) {
    final SyntaxNode name = CSharpSyntax.child(body, MEMBER_NAME).orElseThrow();
    if (CSharpSyntax.children(name, CSharpSyntax.IDENTIFIER).size() > 1) {
      return List.of(); // an explicit interface implementation
    }
    return accessors(
        declaration,
        body,
        mods,
        new Shape(CSharpSyntax.text(name), List.of(), propertyType(body))
    );
  }

  private List<ApiMember> indexer(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods
  ) {
    return accessors(
        declaration,
        body,
        mods,
        new Shape(
            "Item",
            owner.refs().parameters(CSharpSyntax.child(body, "parameter_list")),
            propertyType(body)
        )
    );
  }

  private ApiTypeRef propertyType(final SyntaxNode body) {
    return owner.refs().type(CSharpSyntax.child(body, TYPE).orElseThrow());
  }

  /** What a property or indexer is called, indexes by and has as type. */
  private record Shape(String name, List<ApiParameter> indexes, ApiTypeRef type) {}

  private List<ApiMember> accessors(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods,
      final Shape shape
  ) {
    final SyntaxNode propertyBody =
        CSharpSyntax.child(body, "property_body").orElseThrow();
    final List<SyntaxNode> declared = CSharpSyntax.children(propertyBody, "accessor_declaration");
    final List<ApiMember> found = new ArrayList<>();
    if (declared.isEmpty()) {
      found.add(accessor(declaration, mods, shape, "get", false));
    }
    for (final SyntaxNode accessor : declared) {
      final String keyword =
          CSharpSyntax.child(accessor, "accessor_keyword").map(CSharpSyntax::text).orElse("");
      final boolean bodyless =
          CSharpSyntax.child(accessor, "accessor_body")
              .map(text -> ";".equals(CSharpSyntax.text(text)))
              .orElse(true);
      found.add(accessor(declaration, mods.withAccessor(accessor), shape, keyword, bodyless));
    }
    return found;
  }

  private ApiMember accessor(
      final SyntaxNode declaration,
      final CSharpApiModifiers mods,
      final Shape shape,
      final String keyword,
      final boolean bodyless
  ) {
    final boolean getter = "get".equals(keyword);
    final List<ApiParameter> parameters = new ArrayList<>(shape.indexes());
    if (!getter) {
      parameters.add(new ApiParameter(shape.type(), false));
    }
    return new CSharpApiMemberDraft(
        ApiMember.Kind.METHOD,
        (getter ? "get_" : "set_") + shape.name(),
        mods,
        owner,
        declaration
    )
        .signature(List.of(), parameters, getter ? shape.type() : CSharpApiRefs.voidType())
        .bodyless(bodyless)
        .build();
  }

  private List<ApiMember> event(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods
  ) {
    final ApiTypeRef type = propertyType(body);
    final Optional<SyntaxNode> name = CSharpSyntax.child(body, MEMBER_NAME);
    final List<String> names =
        name.isPresent() ? List.of(CSharpSyntax.text(name.get())) : declaredNames(body);
    final List<ApiMember> found = new ArrayList<>();
    for (final String event : names) {
      for (final String prefix : List.of("add_", "remove_")) {
        found.add(
            new CSharpApiMemberDraft(
                ApiMember.Kind.METHOD,
                prefix + event,
                mods,
                owner,
                declaration
            )
                .signature(
                    List.of(),
                    List.of(new ApiParameter(type, false)),
                    CSharpApiRefs.voidType()
                )
                .bodyless(name.isEmpty())
                .build()
        );
      }
    }
    return found;
  }

  private List<ApiMember> operator(
      final SyntaxNode declaration,
      final SyntaxNode body,
      final CSharpApiModifiers mods
  ) {
    final Optional<SyntaxNode> symbol = CSharpSyntax.child(body, "overloadable_operator");
    final String name =
        symbol.isPresent()
            ? "operator " + CSharpSyntax.text(symbol.get())
            : CSharpSyntax.text(body.children().getFirst()) + " operator";
    final SyntaxNode returned = CSharpSyntax.child(body, TYPE).orElseThrow();
    return List.of(
        new CSharpApiMemberDraft(ApiMember.Kind.METHOD, name, mods, owner, declaration)
            .signature(
                List.of(),
                owner.refs().parameters(CSharpSyntax.child(body, "parameter_list")),
                owner.refs().type(returned)
            )
            .build()
    );
  }
}
