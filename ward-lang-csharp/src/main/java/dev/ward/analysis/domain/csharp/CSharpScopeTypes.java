package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The scopes of the declarations of a C# file: types, their members and parameters, accessors and
 * usings. A type with a base type, or a partial one, is open: the names of its other parts and of
 * its base are not in the file.
 */
final class CSharpScopeTypes {
  private static final Set<String> TYPES =
      Set.of(
          "class_declaration",
          "struct_declaration",
          "record_declaration",
          "interface_declaration"
      );
  private static final Set<String> PARAMETERS =
      Set.of(
          "parameter",
          "explicit_anonymous_function_parameter",
          "implicit_anonymous_function_parameter"
      );
  private static final Set<String> VALUE_ACCESSORS = Set.of("set", "init", "add", "remove");
  private static final Set<String> FIELD_ACCESSORS = Set.of("get", "set", "init");
  private static final String MEMBER_NAME = "member_name";

  private final CSharpScopeWalk walk;
  private boolean partial;

  CSharpScopeTypes(final CSharpScopeWalk walk) {
    this.walk = walk;
  }

  void register(final Map<String, Consumer<SyntaxNode>> handlers) {
    TYPES.forEach(kind -> handlers.put(kind, this::type));
    PARAMETERS.forEach(kind -> handlers.put(kind, this::parameter));
    handlers.put("type_declaration", this::declaration);
    handlers.put("member_declaration", this::declaration);
    handlers.put("enum_declaration", this::enumType);
    handlers.put("extension_declaration", node -> walk.scoped(node, ScopeKind.CLASS, true));
    handlers.put("type_parameter_declaration", this::typeParameter);
    handlers.put("method_declaration", this::method);
    handlers.put("local_function_declaration", this::method);
    handlers.put("property_declaration", this::property);
    handlers.put("event_declaration", this::property);
    handlers.put("accessor_declaration", this::accessor);
  }

  private void declaration(final SyntaxNode node) {
    partial =
        node.children().stream()
            .anyMatch(
                child ->
                    "modifier".equals(child.kind()) && "partial".equals(CSharpSyntax.text(child))
            );
    walk.generic(node);
    partial = false;
  }

  private void type(final SyntaxNode node) {
    final boolean open = partial || CSharpSyntax.child(node, "class_base").isPresent();
    CSharpSyntax.name(node).ifPresent(name -> walk.declare(name, BindingKind.CLASS, false));
    partial = false;
    walk.scoped(node, ScopeKind.CLASS, open);
  }

  private void enumType(final SyntaxNode node) {
    CSharpSyntax.name(node).ifPresent(name -> walk.declare(name, BindingKind.CLASS, false));
    walk.scoped(node, ScopeKind.CLASS, partial);
    partial = false;
  }

  private void typeParameter(final SyntaxNode node) {
    CSharpSyntax.child(node, "type_parameter")
        .flatMap(CSharpSyntax::name)
        .ifPresent(name -> walk.declare(name, BindingKind.TYPE, false));
    CSharpSyntax.children(node, "attributes").forEach(walk::visit);
  }

  private void method(final SyntaxNode node) {
    final Optional<SyntaxNode> name =
        "local_function_declaration".equals(node.kind())
            ? CSharpSyntax.name(node)
            : CSharpSyntax.child(node, MEMBER_NAME).flatMap(CSharpSyntax::lastName);
    name.ifPresent(found -> walk.declare(found, BindingKind.FUNCTION, false, BindingFlag.HOISTED));
    walk.scoped(node, ScopeKind.FUNCTION, false);
  }

  private void property(final SyntaxNode node) {
    CSharpSyntax.child(node, MEMBER_NAME)
        .flatMap(CSharpSyntax::lastName)
        .ifPresent(name -> walk.declare(name, BindingKind.VARIABLE, false));
    walk.generic(node);
  }

  private void accessor(final SyntaxNode node) {
    final String keyword =
        CSharpSyntax.child(node, "accessor_keyword").map(CSharpSyntax::text).orElse("");
    walk.builder().open(ScopeKind.FUNCTION, node.span());
    if (VALUE_ACCESSORS.contains(keyword)) {
      implicit("value", BindingKind.PARAMETER, node);
    }
    if (FIELD_ACCESSORS.contains(keyword)) {
      implicit("field", BindingKind.VARIABLE, node);
    }
    walk.generic(node);
    walk.builder().close();
  }

  private void implicit(final String name, final BindingKind kind, final SyntaxNode accessor) {
    walk.builder().declare(name, kind, accessor.span(), EnumSet.noneOf(BindingFlag.class));
  }

  private void parameter(final SyntaxNode node) {
    CSharpSyntax.name(node).ifPresent(name -> walk.declare(name, BindingKind.PARAMETER, false));
    node.children().stream()
        .filter(child -> !"type_".equals(child.kind()))
        .filter(child -> !CSharpSyntax.IDENTIFIER.equals(child.kind()))
        .forEach(walk::visit);
  }
}
