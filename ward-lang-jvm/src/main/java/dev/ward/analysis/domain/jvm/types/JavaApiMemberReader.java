package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;

/** Reads the fields, methods and constructors of one Java class into API members. */
final class JavaApiMemberReader {
  private final String path;
  private final SourceModel.ClassDecl declaration;
  private final ApiTypeRefs refs;
  private final ApiVisibility type;
  private final ApiLocation typeLocation;

  JavaApiMemberReader(
      final String path,
      final SourceModel.ClassDecl declaration,
      final ApiTypeRefs refs,
      final ApiVisibility type,
      final ApiLocation typeLocation) {
    this.path = path;
    this.declaration = declaration;
    this.refs = refs;
    this.type = type;
    this.typeLocation = typeLocation;
  }

  static boolean isInterface(final JvmClass.Kind kind) {
    return kind == JvmClass.Kind.INTERFACE || kind == JvmClass.Kind.ANNOTATION;
  }

  /** Returns every member, whatever its visibility, so a lowered visibility can be seen. */
  List<ApiMember> members() {
    final List<ApiMember> found = new ArrayList<>();
    declaration.members().stream()
        .flatMap(member -> member.field().stream().map(field -> field(field, member)))
        .forEach(found::add);
    declaration.jvm().methods().stream()
        .filter(method -> !Flags.has(method.access(), Flags.SYNTHETIC))
        .map(this::method)
        .forEach(found::add);
    return found;
  }

  private ApiMember field(final JvmField field, final SourceModel.Member member) {
    return new ApiMember(
        ApiMember.Kind.FIELD,
        field.name(),
        JavaApiTypeReader.visibility(field.access(), false, isInterface(declaration.jvm().kind())),
        field.isStatic(),
        Flags.has(field.access(), Flags.FINAL),
        false,
        false,
        false,
        List.of(),
        List.of(),
        refs.of(field.type()),
        location(member.outer().span()));
  }

  private ApiMember method(final JvmMethod method) {
    final ApiTypeRefs inner = refs.with("M", method.typeParameters());
    final boolean isAbstract = Flags.has(method.access(), Flags.ABSTRACT);
    return new ApiMember(
        method.isConstructor() ? ApiMember.Kind.CONSTRUCTOR : ApiMember.Kind.METHOD,
        method.name(),
        methodVisibility(method),
        method.isStatic(),
        Flags.has(method.access(), Flags.FINAL),
        isAbstract,
        isDefault(method, isAbstract),
        method.isVarargs(),
        inner.parametersOf(method.typeParameters(), ""),
        method.parameters().stream()
            .map(parameter -> new ApiParameter(inner.of(parameter), false))
            .toList(),
        inner.of(method.returnType()),
        memberLocation(method));
  }

  private boolean isDefault(final JvmMethod method, final boolean isAbstract) {
    return isInterface(declaration.jvm().kind())
        && !isAbstract
        && !method.isStatic()
        && !Flags.has(method.access(), Flags.PRIVATE);
  }

  /** An enum constructor is private; an implicit constructor has the access of its class. */
  private ApiVisibility methodVisibility(final JvmMethod method) {
    final JvmClass owner = declaration.jvm();
    if (method.isConstructor() && owner.kind() == JvmClass.Kind.ENUM) {
      return ApiVisibility.PRIVATE;
    }
    if (method.isConstructor() && isImplicit(method)) {
      return type;
    }
    return JavaApiTypeReader.visibility(method.access(), false, isInterface(owner.kind()));
  }

  /** An implicit member is one the reader added that no declaration in the file wrote. */
  private boolean isImplicit(final JvmMethod method) {
    return declaration.members().stream()
        .flatMap(member -> member.method().stream())
        .noneMatch(method::equals);
  }

  private ApiLocation memberLocation(final JvmMethod method) {
    return declaration.members().stream()
        .filter(member -> member.method().filter(method::equals).isPresent())
        .findFirst()
        .map(member -> location(member.outer().span()))
        .orElse(typeLocation);
  }

  private ApiLocation location(final Span span) {
    return new ApiLocation(path, span.startLine(), span.startColumn());
  }
}
