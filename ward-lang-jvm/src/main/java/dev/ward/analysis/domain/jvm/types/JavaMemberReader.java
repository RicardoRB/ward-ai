package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the fields, methods and constructors of one Java type declaration. */
final class JavaMemberReader {
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_TYPE = "typeType";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String VOID = "void";

  private final TypeScope scope;
  private final String owner;
  private final JvmClass.Kind kind;

  JavaMemberReader(final TypeScope scope, final String owner, final JvmClass.Kind kind) {
    this.scope = scope;
    this.owner = owner;
    this.kind = kind;
  }

  List<SourceModel.Member> read(final SyntaxNode type) {
    final List<SourceModel.Member> members = new ArrayList<>();
    enumConstants(type, members);
    recordComponents(type, members);
    for (final JavaClassReader.Entry entry : JavaClassReader.bodyEntries(type)) {
      final SyntaxNode node = entry.declaration();
      if ("fieldDeclaration".equals(node.kind()) || "constDeclaration".equals(node.kind())) {
        members.add(fields(entry));
      } else if ("block".equals(node.kind())) {
        members.add(
            new SourceModel.Member(
                node,
                entry.outer(),
                scope,
                Optional.empty(),
                List.of(),
                List.of(),
                Optional.of(node)));
      } else if (!JavaClassReader.isType(node)) {
        method(entry).ifPresent(members::add);
      }
    }
    return members;
  }

  private boolean inInterface() {
    return kind == JvmClass.Kind.INTERFACE || kind == JvmClass.Kind.ANNOTATION;
  }

  private SourceModel.Member fields(final JavaClassReader.Entry entry) {
    final SyntaxNode node = entry.declaration();
    final Optional<JType> base =
        Syntax.child(node, TYPE_TYPE).flatMap(type -> JavaTypes.type(type, scope));
    int flags = JavaModifiers.flags(entry.modifiers());
    if (inInterface()) {
      flags |= Flags.PUBLIC | Flags.STATIC | Flags.FINAL;
    }
    final int fieldFlags = flags;
    final List<String> annotations = JavaModifiers.annotations(entry.modifiers(), scope);
    final List<JvmField> fields = new ArrayList<>();
    final List<SourceModel.Variable> variables = new ArrayList<>();
    for (final SyntaxNode declarator : declarators(node)) {
      final SyntaxNode id = Syntax.child(declarator, DECLARATOR_ID).orElse(declarator);
      final SyntaxNode name = Syntax.child(id, IDENTIFIER).orElse(declarator);
      final Optional<JType> type = base.map(found -> JavaTypes.arrayOf(found, dimensions(id)));
      variables.add(new SourceModel.Variable(name, type));
      final Optional<Object> value =
          (fieldFlags & Flags.FINAL) != 0 ? constantOf(declarator) : Optional.empty();
      type.ifPresent(
          found ->
              fields.add(new JvmField(Syntax.text(name), fieldFlags, found, value, annotations)));
    }
    return new SourceModel.Member(
        node, entry.outer(), scope, Optional.empty(), fields, variables, Optional.of(node));
  }

  private static List<SyntaxNode> declarators(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if ("variableDeclarators".equals(child.kind())) {
        found.addAll(Syntax.children(child, "variableDeclarator"));
      } else if ("constantDeclarator".equals(child.kind())) {
        found.add(child);
      }
    }
    return found;
  }

  private static int dimensions(final SyntaxNode id) {
    return (int) id.children().stream().filter(child -> "[".equals(Syntax.token(child))).count();
  }

  private static Optional<Object> constantOf(final SyntaxNode declarator) {
    return Syntax.find(declarator, "expression").flatMap(Constants::literalValue);
  }

  private void enumConstants(final SyntaxNode type, final List<SourceModel.Member> members) {
    if (kind != JvmClass.Kind.ENUM) {
      return;
    }
    final JType self = JType.ClassType.of(owner);
    Syntax.child(type, "enumConstants").stream()
        .flatMap(list -> Syntax.children(list, "enumConstant").stream())
        .forEach(
            constant -> {
              final SyntaxNode name = Syntax.child(constant, IDENTIFIER).orElse(constant);
              final JvmField field =
                  new JvmField(
                      Syntax.text(name),
                      Flags.PUBLIC | Flags.STATIC | Flags.FINAL | Flags.ENUM,
                      self,
                      Optional.empty(),
                      List.of());
              members.add(
                  new SourceModel.Member(
                      constant,
                      constant,
                      scope,
                      Optional.empty(),
                      List.of(field),
                      List.of(new SourceModel.Variable(name, Optional.of(self))),
                      Optional.of(constant)));
            });
  }

  private void recordComponents(final SyntaxNode type, final List<SourceModel.Member> members) {
    if (kind != JvmClass.Kind.RECORD) {
      return;
    }
    Syntax.child(type, "recordHeader")
        .flatMap(header -> Syntax.child(header, "recordComponentList"))
        .stream()
        .flatMap(list -> Syntax.children(list, "recordComponent").stream())
        .forEach(
            component -> {
              final SyntaxNode name = Syntax.child(component, IDENTIFIER).orElse(component);
              final Optional<JType> written =
                  Syntax.child(component, TYPE_TYPE).flatMap(found -> JavaTypes.type(found, scope));
              final List<JvmField> fields =
                  written.stream()
                      .map(
                          found ->
                              new JvmField(
                                  Syntax.text(name),
                                  Flags.PRIVATE | Flags.FINAL,
                                  found,
                                  Optional.empty(),
                                  List.of()))
                      .toList();
              members.add(
                  new SourceModel.Member(
                      component,
                      component,
                      scope,
                      Optional.empty(),
                      fields,
                      List.of(new SourceModel.Variable(name, written)),
                      Optional.empty()));
            });
  }

  private Optional<SourceModel.Member> method(final JavaClassReader.Entry entry) {
    final SyntaxNode node = entry.declaration();
    if ("compactConstructorDeclaration".equals(node.kind())) {
      return Optional.of(
          new SourceModel.Member(
              node,
              entry.outer(),
              scope,
              Optional.empty(),
              List.of(),
              List.of(),
              Syntax.child(node, "block")));
    }
    final Optional<SyntaxNode> core = unwrap(node);
    if (core.isEmpty()) {
      return Optional.empty();
    }
    final List<TypeParameter> typeParameters =
        node.kind().startsWith("generic") ? JavaTypes.typeParameters(node, scope) : List.of();
    final TypeScope inner =
        scope.withTypeVariables(typeParameters.stream().map(TypeParameter::name).toList());
    final List<SyntaxNode> modifiers = new ArrayList<>(entry.modifiers());
    modifiers.addAll(Syntax.children(node, "interfaceMethodModifier"));
    modifiers.addAll(Syntax.children(core.get(), "interfaceMethodModifier"));
    return Optional.of(member(entry, core.get(), typeParameters, inner, modifiers));
  }

  /** The parameters of a method: their variables and types, and whether the last is variadic. */
  private record Parameters(
      List<SourceModel.Variable> variables, List<JType> types, boolean varargs, boolean resolved) {}

  private Parameters parameters(final SyntaxNode core, final TypeScope inner) {
    final List<SourceModel.Variable> variables = new ArrayList<>();
    final List<JType> types = new ArrayList<>();
    boolean varargs = false;
    boolean resolved = true;
    for (final SyntaxNode parameter : formalParameters(core)) {
      final SyntaxNode id = Syntax.child(parameter, DECLARATOR_ID).orElse(parameter);
      final boolean variadic = Syntax.hasToken(parameter, "...");
      varargs = variadic;
      final Optional<JType> type =
          Syntax.child(parameter, TYPE_TYPE)
              .flatMap(written -> JavaTypes.type(written, inner))
              .map(found -> JavaTypes.arrayOf(found, dimensions(id) + (variadic ? 1 : 0)));
      variables.add(new SourceModel.Variable(Syntax.child(id, IDENTIFIER).orElse(id), type));
      type.ifPresent(types::add);
      resolved &= type.isPresent();
    }
    return new Parameters(variables, types, varargs, resolved);
  }

  private int flagsOf(
      final List<SyntaxNode> modifiers, final boolean declaredBody, final boolean varargs) {
    int flags = JavaModifiers.flags(modifiers);
    if (inInterface() && (flags & Flags.PRIVATE) == 0) {
      flags |= Flags.PUBLIC;
    }
    if (inInterface() && !declaredBody && (flags & Flags.STATIC) == 0) {
      flags |= Flags.ABSTRACT;
    }
    return varargs ? flags | Flags.VARARGS : flags;
  }

  private SourceModel.Member member(
      final JavaClassReader.Entry entry,
      final SyntaxNode core,
      final List<TypeParameter> typeParameters,
      final TypeScope inner,
      final List<SyntaxNode> modifiers) {
    final boolean constructor = "constructorDeclaration".equals(core.kind());
    final Parameters parameters = parameters(core, inner);
    final Optional<JType> returns =
        constructor
            ? Optional.of(new JType.Primitive(VOID))
            : Syntax.child(core, "typeTypeOrVoid")
                .flatMap(written -> JavaTypes.typeOrVoid(written, inner));
    final Optional<SyntaxNode> body =
        constructor
            ? Syntax.child(core, "block")
            : Syntax.child(core, "methodBody").flatMap(b -> Syntax.child(b, "block"));
    final List<JType> thrown =
        Syntax.child(core, "qualifiedNameList").stream()
            .flatMap(list -> Syntax.children(list, "qualifiedName").stream())
            .map(name -> inner.resolve(List.of(Syntax.text(name).split("\\."))))
            .flatMap(Optional::stream)
            .map(name -> (JType) JType.ClassType.of(name))
            .toList();
    final String name =
        constructor
            ? JvmMethod.CONSTRUCTOR
            : Syntax.text(Syntax.child(core, IDENTIFIER).orElse(core));
    final int flags = flagsOf(modifiers, body.isPresent(), parameters.varargs());
    final Optional<JvmMethod> method =
        parameters.resolved() && returns.isPresent()
            ? Optional.of(
                new JvmMethod(
                    name,
                    flags,
                    typeParameters,
                    parameters.types(),
                    returns.get(),
                    thrown,
                    JavaModifiers.annotations(modifiers, inner),
                    List.of()))
            : Optional.empty();
    return new SourceModel.Member(
        core, entry.outer(), inner, method, List.of(), parameters.variables(), body);
  }

  private static Optional<SyntaxNode> unwrap(final SyntaxNode node) {
    return switch (node.kind()) {
      case "methodDeclaration", "constructorDeclaration", "interfaceCommonBodyDeclaration" ->
          Optional.of(node);
      case "genericMethodDeclaration", "genericConstructorDeclaration" ->
          node.children().stream()
              .filter(child -> child.kind().endsWith("Declaration"))
              .findFirst();
      case "interfaceMethodDeclaration", "genericInterfaceMethodDeclaration" ->
          Syntax.child(node, "interfaceCommonBodyDeclaration");
      default -> Optional.empty();
    };
  }

  private static List<SyntaxNode> formalParameters(final SyntaxNode core) {
    final List<SyntaxNode> found = new ArrayList<>();
    Syntax.child(core, "formalParameters")
        .ifPresent(
            list -> {
              for (final SyntaxNode child : list.children()) {
                if ("formalParameter".equals(child.kind())) {
                  found.add(child);
                } else if ("formalParameterList".equals(child.kind())) {
                  found.addAll(Syntax.children(child, "formalParameter"));
                }
              }
            });
    return found;
  }

  /** Returns the methods of the members, with the implicit ones of the kind of class. */
  List<JvmMethod> methods(
      final List<SourceModel.Member> members, final boolean defaultConstructor) {
    final List<JvmMethod> methods =
        new ArrayList<>(members.stream().flatMap(member -> member.method().stream()).toList());
    final boolean hasConstructor = methods.stream().anyMatch(JvmMethod::isConstructor);
    if (defaultConstructor && !hasConstructor) {
      methods.add(implicit(JvmMethod.CONSTRUCTOR, 0, List.of(), new JType.Primitive(VOID)));
    }
    if (kind == JvmClass.Kind.ENUM) {
      final JType self = JType.ClassType.of(owner);
      methods.add(implicit("values", Flags.STATIC, List.of(), new JType.ArrayType(self)));
      methods.add(
          implicit("valueOf", Flags.STATIC, List.of(JType.ClassType.of("java.lang.String")), self));
    }
    if (kind == JvmClass.Kind.RECORD) {
      addRecordMembers(members, methods);
    }
    return methods;
  }

  private static JvmMethod implicit(
      final String name, final int extra, final List<JType> parameters, final JType returns) {
    return new JvmMethod(
        name,
        Flags.PUBLIC | extra,
        List.of(),
        parameters,
        returns,
        List.of(),
        List.of(),
        List.of());
  }

  private static void addRecordMembers(
      final List<SourceModel.Member> members, final List<JvmMethod> methods) {
    final List<JvmField> components =
        members.stream()
            .filter(member -> "recordComponent".equals(member.node().kind()))
            .flatMap(member -> member.field().stream())
            .toList();
    for (final JvmField component : components) {
      if (methods.stream()
          .noneMatch(m -> m.name().equals(component.name()) && m.parameters().isEmpty())) {
        methods.add(implicit(component.name(), 0, List.of(), component.type()));
      }
    }
    final List<JType> types = components.stream().map(JvmField::type).toList();
    if (methods.stream().noneMatch(m -> m.isConstructor() && m.parameters().equals(types))) {
      methods.add(implicit(JvmMethod.CONSTRUCTOR, 0, types, new JType.Primitive(VOID)));
    }
  }
}
