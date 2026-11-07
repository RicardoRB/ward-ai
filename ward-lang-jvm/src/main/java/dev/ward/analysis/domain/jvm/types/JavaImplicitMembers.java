package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The members a Java type has without writing them: enum constants, record parts, defaults. */
final class JavaImplicitMembers {
  private static final String IDENTIFIER = "identifier";
  private static final String VOID = "void";
  private static final String RECORD_COMPONENT = "recordComponent";

  private JavaImplicitMembers() {}

  static List<SourceModel.Member> enumConstants(
      final SyntaxNode type, final TypeScope scope, final String owner, final JvmClass.Kind kind) {
    if (kind != JvmClass.Kind.ENUM) {
      return List.of();
    }
    final JType self = JType.ClassType.of(owner);
    return Syntax.child(type, "enumConstants").stream()
        .flatMap(list -> Syntax.children(list, "enumConstant").stream())
        .map(constant -> enumConstant(constant, scope, self))
        .toList();
  }

  private static SourceModel.Member enumConstant(
      final SyntaxNode constant, final TypeScope scope, final JType self) {
    final SyntaxNode name = Syntax.child(constant, IDENTIFIER).orElse(constant);
    final JvmField field =
        new JvmField(
            Syntax.text(name),
            Flags.PUBLIC | Flags.STATIC | Flags.FINAL | Flags.ENUM,
            self,
            Optional.empty(),
            List.of());
    return new SourceModel.Member(
        constant,
        constant,
        scope,
        Optional.empty(),
        List.of(field),
        List.of(new SourceModel.Variable(name, Optional.of(self))),
        Optional.of(constant));
  }

  static List<SourceModel.Member> recordComponents(
      final SyntaxNode type, final TypeScope scope, final JvmClass.Kind kind) {
    if (kind != JvmClass.Kind.RECORD) {
      return List.of();
    }
    return Syntax.child(type, "recordHeader")
        .flatMap(header -> Syntax.child(header, "recordComponentList"))
        .stream()
        .flatMap(list -> Syntax.children(list, RECORD_COMPONENT).stream())
        .map(component -> recordComponent(component, scope))
        .toList();
  }

  private static SourceModel.Member recordComponent(
      final SyntaxNode component, final TypeScope scope) {
    final SyntaxNode name = Syntax.child(component, IDENTIFIER).orElse(component);
    final Optional<JType> written =
        Syntax.child(component, "typeType").flatMap(found -> JavaTypes.type(found, scope));
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
    return new SourceModel.Member(
        component,
        component,
        scope,
        Optional.empty(),
        fields,
        List.of(new SourceModel.Variable(name, written)),
        Optional.empty());
  }

  /** Returns the methods of the members, with the implicit ones of the kind of class. */
  static List<JvmMethod> methods(
      final List<SourceModel.Member> members,
      final String owner,
      final JvmClass.Kind kind,
      final boolean defaultConstructor) {
    final List<JvmMethod> methods =
        new ArrayList<>(members.stream().flatMap(member -> member.method().stream()).toList());
    if (defaultConstructor && methods.stream().noneMatch(JvmMethod::isConstructor)) {
      methods.add(implicit(JvmMethod.CONSTRUCTOR, 0, List.of(), new JType.Primitive(VOID)));
    }
    if (kind == JvmClass.Kind.ENUM) {
      final JType self = JType.ClassType.of(owner);
      methods.add(implicit("values", Flags.STATIC, List.of(), new JType.ArrayType(self)));
      methods.add(
          implicit("valueOf", Flags.STATIC, List.of(JType.ClassType.of(Types.STRING)), self));
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
            .filter(member -> RECORD_COMPONENT.equals(member.node().kind()))
            .flatMap(member -> member.field().stream())
            .toList();
    for (final JvmField component : components) {
      final boolean declared =
          methods.stream()
              .anyMatch(m -> m.name().equals(component.name()) && m.parameters().isEmpty());
      if (!declared) {
        methods.add(implicit(component.name(), 0, List.of(), component.type()));
      }
    }
    final List<JType> types = components.stream().map(JvmField::type).toList();
    if (methods.stream().noneMatch(m -> m.isConstructor() && m.parameters().equals(types))) {
      methods.add(implicit(JvmMethod.CONSTRUCTOR, 0, types, new JType.Primitive(VOID)));
    }
  }
}
