package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiLocation;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.api.ApiVisibility;
import dev.ward.analysis.domain.ir.Span;
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
            new JavaApiMemberReader(path, declaration, refs, visibility, at).members(),
            at);
    found.add(type);
    final boolean isInterface = JavaApiMemberReader.isInterface(jvm.kind());
    for (final SourceModel.ClassDecl nested : declaration.nested()) {
      read(nested, type, isInterface, refs, found);
    }
  }

  static ApiVisibility visibility(
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
}
