package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads one Kotlin class declaration into a {@link JvmClass}. */
final class KotlinClassReader {
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";

  private KotlinClassReader() {}

  static KotlinUnitModel.Declared read(
      final SyntaxNode node, final String binary, final TypeScope scope) {
    final List<TypeParameter> parameters = typeParameters(node);
    final TypeScope inner =
        scope.inClass(binary, parameters.stream().map(TypeParameter::name).toList());
    final boolean isInterface = Syntax.hasToken(node, "interface");
    final List<JvmField> fields = new ArrayList<>();
    final List<JvmMethod> methods = new ArrayList<>();
    constructor(node, inner, isInterface, fields, methods);
    final List<SyntaxNode> members = KotlinUnitModel.members(node);
    for (final SyntaxNode member : members) {
      if ("propertyDeclaration".equals(member.kind())) {
        propertyMember(member, inner, fields, methods);
      } else if ("functionDeclaration".equals(member.kind())) {
        function(member, inner).ifPresent(methods::add);
      }
    }
    final List<JType.ClassType> interfaces = new ArrayList<>();
    final Optional<JType.ClassType> superclass = supertypes(node, inner, interfaces);
    final int flags = Syntax.text(node).startsWith("abstract") ? Flags.ABSTRACT : 0;
    final JvmClass jvm =
        new JvmClass(
            binary,
            isInterface ? JvmClass.Kind.INTERFACE : JvmClass.Kind.CLASS,
            Flags.PUBLIC | flags,
            isInterface ? Optional.empty() : superclass,
            interfaces,
            parameters,
            fields,
            methods,
            List.of(),
            List.of(),
            false,
            true);
    return new KotlinUnitModel.Declared(jvm, node, inner, members);
  }

  private static void constructor(
      final SyntaxNode node,
      final TypeScope inner,
      final boolean isInterface,
      final List<JvmField> fields,
      final List<JvmMethod> methods) {
    final List<JType> types = new ArrayList<>();
    boolean resolved = true;
    for (final SyntaxNode parameter : constructorParameters(node)) {
      final Optional<JType> type =
          Syntax.child(parameter, "type").flatMap(written -> KotlinTypes.type(written, inner));
      resolved &= type.isPresent();
      type.ifPresent(types::add);
      if (type.isPresent()
          && (Syntax.hasToken(parameter, "val") || Syntax.hasToken(parameter, "var"))) {
        property(
            Syntax.text(Syntax.child(parameter, SIMPLE_IDENTIFIER).orElse(parameter)),
            type.get(),
            Syntax.hasToken(parameter, "var"),
            fields,
            methods);
      }
    }
    if (resolved && !isInterface) {
      methods.add(
          new JvmMethod(
              JvmMethod.CONSTRUCTOR,
              Flags.PUBLIC,
              List.of(),
              types,
              new JType.Primitive("void"),
              List.of(),
              List.of(),
              List.of()));
    }
  }

  private static Optional<JType.ClassType> supertypes(
      final SyntaxNode node, final TypeScope inner, final List<JType.ClassType> interfaces) {
    Optional<JType.ClassType> superclass = Optional.of(JType.ClassType.of(Types.OBJECT));
    final List<SyntaxNode> specifiers =
        Syntax.find(node, "delegationSpecifiers")
            .map(found -> Syntax.children(found, "delegationSpecifier"))
            .orElse(List.of());
    for (final SyntaxNode specifier : specifiers) {
      final Optional<JType.ClassType> type =
          Syntax.find(specifier, "userType")
              .flatMap(user -> KotlinTypes.type(user, inner))
              .filter(JType.ClassType.class::isInstance)
              .map(JType.ClassType.class::cast);
      if (type.isPresent() && Syntax.find(specifier, "constructorInvocation").isPresent()) {
        superclass = type;
      } else {
        type.ifPresent(interfaces::add);
      }
    }
    return superclass;
  }

  private static List<TypeParameter> typeParameters(final SyntaxNode node) {
    return Syntax.child(node, "typeParameters").stream()
        .flatMap(list -> Syntax.children(list, "typeParameter").stream())
        .map(
            parameter ->
                new TypeParameter(
                    Syntax.text(Syntax.child(parameter, SIMPLE_IDENTIFIER).orElse(parameter)),
                    List.of()))
        .toList();
  }

  private static List<SyntaxNode> constructorParameters(final SyntaxNode node) {
    return Syntax.child(node, "primaryConstructor")
        .flatMap(ctor -> Syntax.child(ctor, "classParameters"))
        .map(parameters -> Syntax.children(parameters, "classParameter"))
        .orElse(List.of());
  }

  private static void property(
      final String name,
      final JType type,
      final boolean mutable,
      final List<JvmField> fields,
      final List<JvmMethod> methods) {
    fields.add(new JvmField(name, Flags.PRIVATE, type, Optional.empty(), List.of()));
    final String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    final boolean flag = type.equals(new JType.Primitive("boolean"));
    methods.add(
        new JvmMethod(
            (flag ? "is" : "get") + suffix,
            Flags.PUBLIC,
            List.of(),
            List.of(),
            type,
            List.of(),
            List.of(),
            List.of()));
    if (mutable) {
      methods.add(
          new JvmMethod(
              "set" + suffix,
              Flags.PUBLIC,
              List.of(),
              List.of(type),
              new JType.Primitive("void"),
              List.of(),
              List.of(),
              List.of()));
    }
  }

  private static void propertyMember(
      final SyntaxNode member,
      final TypeScope inner,
      final List<JvmField> fields,
      final List<JvmMethod> methods) {
    final Optional<SyntaxNode> variable = Syntax.child(member, "variableDeclaration");
    final Optional<JType> type =
        variable
            .flatMap(found -> Syntax.child(found, "type"))
            .flatMap(written -> KotlinTypes.type(written, inner));
    if (variable.isPresent() && type.isPresent()) {
      property(
          Syntax.text(Syntax.child(variable.get(), SIMPLE_IDENTIFIER).orElse(variable.get())),
          type.get(),
          Syntax.hasToken(member, "var"),
          fields,
          methods);
    }
  }

  static Optional<JvmMethod> function(final SyntaxNode member, final TypeScope inner) {
    final String name = Syntax.text(Syntax.child(member, "identifier").orElse(member));
    final List<JType> parameters = new ArrayList<>();
    for (final SyntaxNode list : Syntax.children(member, "functionValueParameters")) {
      for (final SyntaxNode holder : Syntax.children(list, "functionValueParameter")) {
        final Optional<JType> type =
            Syntax.find(holder, "type").flatMap(written -> KotlinTypes.type(written, inner));
        if (type.isEmpty()) {
          return Optional.empty();
        }
        parameters.add(type.get());
      }
    }
    final Optional<SyntaxNode> declared = Syntax.child(member, "type");
    final Optional<JType> returns =
        declared.isPresent()
            ? declared.flatMap(written -> KotlinTypes.type(written, inner))
            : Syntax.find(member, "functionBody")
                .filter(body -> Syntax.child(body, "block").isPresent())
                .map(body -> new JType.Primitive("void"));
    return returns.map(
        found ->
            new JvmMethod(
                name, Flags.PUBLIC, List.of(), parameters, found, List.of(), List.of(), List.of()));
  }
}
