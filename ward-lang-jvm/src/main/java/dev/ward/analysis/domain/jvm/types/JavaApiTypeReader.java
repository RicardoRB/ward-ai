package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Turns the classes of one Java file into the API types and members that outside code can use. */
final class JavaApiTypeReader {
  private static final Set<String> IMPLICIT_SUPERTYPES =
      Set.of("java.lang.Object", "java.lang.Enum", "java.lang.Record");

  private final String path;

  JavaApiTypeReader(final String path) {
    this.path = path;
  }

  /** Adds the API types of {@code declaration} and of its member classes to {@code found}. */
  void read(
      final SourceModel.ClassDecl declaration,
      final ApiType parent,
      final boolean parentIsInterface,
      final ApiTypeRefs outer,
      final List<ApiType> found) {
    final JvmClass jvm = declaration.jvm();
    final ApiVisibility visibility = visibility(jvm.access(), parent == null, parentIsInterface);
    final ApiTypeRefs refs = outer.with("C", jvm.typeParameters());
    final ApiLocation at = location(declaration.outer().span());
    final boolean isFinal = isFinal(jvm);
    final ApiType type =
        new ApiType(
            qualified(jvm.name()),
            kind(jvm.kind()),
            visibility,
            isFinal,
            jvm.sealed(),
            jvm.isAbstract(),
            refs.parametersOf(jvm.typeParameters(), ""),
            supertypes(jvm, refs),
            members(declaration, refs, visibility, at),
            at);
    found.add(type);
    final boolean isInterface = isInterface(jvm.kind());
    for (final SourceModel.ClassDecl nested : declaration.nested()) {
      read(nested, type, isInterface, refs, found);
    }
  }

  private static ApiVisibility visibility(
      final int access, final boolean topLevel, final boolean inInterface) {
    if (Flags.has(access, Flags.PUBLIC) || !topLevel && inInterface) {
      return Flags.has(access, Flags.PRIVATE) ? ApiVisibility.PRIVATE : ApiVisibility.PUBLIC;
    }
    if (Flags.has(access, Flags.PROTECTED)) {
      return ApiVisibility.PROTECTED;
    }
    return Flags.has(access, Flags.PRIVATE) ? ApiVisibility.PRIVATE : ApiVisibility.PACKAGE;
  }

  private static boolean isFinal(final JvmClass jvm) {
    return jvm.isFinal() || jvm.kind() == JvmClass.Kind.RECORD || jvm.kind() == JvmClass.Kind.ENUM;
  }

  private static boolean isInterface(final JvmClass.Kind kind) {
    return kind == JvmClass.Kind.INTERFACE || kind == JvmClass.Kind.ANNOTATION;
  }

  private static ApiType.Kind kind(final JvmClass.Kind kind) {
    return switch (kind) {
      case INTERFACE -> ApiType.Kind.INTERFACE;
      case ENUM -> ApiType.Kind.ENUM;
      case RECORD -> ApiType.Kind.RECORD;
      case ANNOTATION -> ApiType.Kind.ANNOTATION;
      default -> ApiType.Kind.CLASS;
    };
  }

  private static String qualified(final String binaryName) {
    return ApiTypeRefs.source(binaryName);
  }

  private ApiLocation location(final Span span) {
    return new ApiLocation(path, span.startLine(), span.startColumn());
  }

  private static List<ApiTypeRef> supertypes(final JvmClass jvm, final ApiTypeRefs refs) {
    return jvm.supertypes().stream()
        .filter(type -> !IMPLICIT_SUPERTYPES.contains(type.name()))
        .map(refs::of)
        .toList();
  }

  /** An implicit member is one the reader added that no declaration in the file wrote. */
  private static boolean isImplicit(
      final JvmMethod method, final SourceModel.ClassDecl declaration) {
    return declaration.members().stream()
        .flatMap(member -> member.method().stream())
        .noneMatch(declared -> declared == method);
  }

  private List<ApiMember> members(
      final SourceModel.ClassDecl declaration,
      final ApiTypeRefs refs,
      final ApiVisibility type,
      final ApiLocation at) {
    final JvmClass jvm = declaration.jvm();
    final List<ApiMember> found = new ArrayList<>();
    for (final SourceModel.Member member : declaration.members()) {
      final ApiLocation where = location(member.outer().span());
      member.field().forEach(field -> found.add(field(field, jvm, refs, where)));
    }
    for (final JvmMethod method : jvm.methods()) {
      final boolean implicit = isImplicit(method, declaration);
      method(method, declaration, refs, new Access(type, implicit), at, found);
    }
    return found;
  }

  private ApiMember field(
      final JvmField field, final JvmClass owner, final ApiTypeRefs refs, final ApiLocation at) {
    final ApiVisibility visibility = visibility(field.access(), false, isInterface(owner.kind()));
    return new ApiMember(
        ApiMember.Kind.FIELD,
        field.name(),
        visibility,
        field.isStatic(),
        Flags.has(field.access(), Flags.FINAL),
        false,
        false,
        false,
        List.of(),
        List.of(),
        refs.of(field.type()),
        at);
  }

  /** What decides the visibility of an implicit member: the type's visibility. */
  private record Access(ApiVisibility type, boolean implicit) {}

  private void method(
      final JvmMethod method,
      final SourceModel.ClassDecl declaration,
      final ApiTypeRefs outer,
      final Access access,
      final ApiLocation typeLocation,
      final List<ApiMember> found) {
    final JvmClass owner = declaration.jvm();
    final ApiVisibility visibility = methodVisibility(method, owner, access);
    if (Flags.has(method.access(), Flags.SYNTHETIC)) {
      return;
    }
    final ApiTypeRefs refs = outer.with("M", method.typeParameters());
    final boolean inInterface = isInterface(owner.kind());
    final boolean isAbstract = Flags.has(method.access(), Flags.ABSTRACT);
    found.add(
        new ApiMember(
            method.isConstructor() ? ApiMember.Kind.CONSTRUCTOR : ApiMember.Kind.METHOD,
            method.name(),
            visibility,
            method.isStatic(),
            Flags.has(method.access(), Flags.FINAL),
            isAbstract,
            inInterface
                && !isAbstract
                && !method.isStatic()
                && !Flags.has(method.access(), Flags.PRIVATE),
            method.isVarargs(),
            refs.parametersOf(method.typeParameters(), ""),
            method.parameters().stream()
                .map(type -> new ApiParameter(refs.of(type), false))
                .toList(),
            refs.of(method.returnType()),
            memberLocation(method, declaration, typeLocation)));
  }

  private static ApiVisibility methodVisibility(
      final JvmMethod method, final JvmClass owner, final Access access) {
    if (method.isConstructor() && owner.kind() == JvmClass.Kind.ENUM) {
      return ApiVisibility.PRIVATE;
    }
    if (access.implicit() && method.isConstructor()) {
      return access.type();
    }
    return visibility(method.access(), false, isInterface(owner.kind()));
  }

  private ApiLocation memberLocation(
      final JvmMethod method, final SourceModel.ClassDecl declaration, final ApiLocation fallback) {
    return declaration.members().stream()
        .filter(member -> member.method().filter(declared -> declared == method).isPresent())
        .findFirst()
        .map(member -> location(member.outer().span()))
        .orElse(fallback);
  }
}
