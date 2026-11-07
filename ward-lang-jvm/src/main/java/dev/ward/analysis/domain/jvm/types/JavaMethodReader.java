package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads a method or constructor declaration of Java. */
final class JavaMethodReader {
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_TYPE = "typeType";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String VOID = "void";
  private static final String BLOCK = "block";
  private static final String GENERIC = "generic";
  private static final String INTERFACE_MODIFIER = "interfaceMethodModifier";
  private static final String COMMON_BODY = "interfaceCommonBodyDeclaration";
  private static final String VARARGS = "...";

  private JavaMethodReader() {}

  /** The parameters of a method: their variables and types, and whether the last is variadic. */
  private record Parameters(
      List<SourceModel.Variable> variables, List<JType> types, boolean varargs, boolean resolved) {}

  /** Reads the method declared by {@code entry}, empty when it is not one. */
  static Optional<SourceModel.Member> method(
      final JavaClassReader.Entry entry, final TypeScope scope, final boolean inInterface) {
    return unwrap(entry.declaration()).map(core -> member(entry, core, scope, inInterface));
  }

  private static Parameters parameters(final SyntaxNode core, final TypeScope inner) {
    final List<SourceModel.Variable> variables = new ArrayList<>();
    final List<JType> types = new ArrayList<>();
    boolean varargs = false;
    boolean resolved = true;
    for (final SyntaxNode parameter : formalParameters(core)) {
      final SyntaxNode id = Syntax.child(parameter, DECLARATOR_ID).orElse(parameter);
      final boolean variadic = Syntax.hasToken(parameter, VARARGS);
      varargs = variadic;
      final int extra = JavaMemberReader.dimensions(id) + (variadic ? 1 : 0);
      final Optional<JType> type =
          Syntax.child(parameter, TYPE_TYPE)
              .flatMap(written -> JavaTypes.type(written, inner))
              .map(found -> JavaTypes.arrayOf(found, extra));
      variables.add(new SourceModel.Variable(Syntax.child(id, IDENTIFIER).orElse(id), type));
      type.ifPresent(types::add);
      resolved &= type.isPresent();
    }
    return new Parameters(variables, types, varargs, resolved);
  }

  private static int flagsOf(
      final List<SyntaxNode> modifiers,
      final boolean inInterface,
      final boolean declaredBody,
      final boolean varargs) {
    int flags = JavaModifiers.flags(modifiers);
    if (inInterface && !Flags.has(flags, Flags.PRIVATE)) {
      flags |= Flags.PUBLIC;
    }
    if (inInterface && !declaredBody && !Flags.has(flags, Flags.STATIC)) {
      flags |= Flags.ABSTRACT;
    }
    return varargs ? flags | Flags.VARARGS : flags;
  }

  private static SourceModel.Member member(
      final JavaClassReader.Entry entry,
      final SyntaxNode core,
      final TypeScope scope,
      final boolean inInterface) {
    final SyntaxNode node = entry.declaration();
    final List<TypeParameter> typeParameters =
        node.kind().startsWith(GENERIC) ? JavaTypeParameters.of(node, scope) : List.of();
    final TypeScope inner =
        scope.withTypeVariables(typeParameters.stream().map(TypeParameter::name).toList());
    final List<SyntaxNode> modifiers = new ArrayList<>(entry.modifiers());
    modifiers.addAll(Syntax.children(node, INTERFACE_MODIFIER));
    modifiers.addAll(Syntax.children(core, INTERFACE_MODIFIER));
    final boolean constructor = "constructorDeclaration".equals(core.kind());
    final Parameters parameters = parameters(core, inner);
    final Optional<JType> returns =
        constructor
            ? Optional.of(new JType.Primitive(VOID))
            : Syntax.child(core, "typeTypeOrVoid")
                .flatMap(written -> JavaTypes.typeOrVoid(written, inner));
    final Optional<SyntaxNode> body =
        constructor
            ? Syntax.child(core, BLOCK)
            : Syntax.child(core, "methodBody").flatMap(b -> Syntax.child(b, BLOCK));
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
    final int flags = flagsOf(modifiers, inInterface, body.isPresent(), parameters.varargs());
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
      case "methodDeclaration", "constructorDeclaration", COMMON_BODY -> Optional.of(node);
      case "genericMethodDeclaration", "genericConstructorDeclaration" ->
          node.children().stream()
              .filter(child -> child.kind().endsWith("Declaration"))
              .findFirst();
      case "interfaceMethodDeclaration", "genericInterfaceMethodDeclaration" ->
          Syntax.child(node, COMMON_BODY);
      default -> Optional.empty();
    };
  }

  private static List<SyntaxNode> formalParameters(final SyntaxNode core) {
    final List<SyntaxNode> found = new ArrayList<>();
    Syntax.child(core, "formalParameters")
        .ifPresent(
            list -> {
              found.addAll(Syntax.children(list, "formalParameter"));
              Syntax.children(list, "formalParameterList")
                  .forEach(more -> found.addAll(Syntax.children(more, "formalParameter")));
            });
    return found;
  }
}
