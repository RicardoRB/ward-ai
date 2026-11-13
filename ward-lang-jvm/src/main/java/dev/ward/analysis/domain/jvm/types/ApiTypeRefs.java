package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns the types of a declaration into API type references. Type variables are keyed by their
 * position, so renaming one does not change the type.
 */
final class ApiTypeRefs {
  private static final String OBJECT = "java.lang.Object";
  private static final String ARRAY = "[]";
  private static final String SEPARATOR = ", ";

  private final Map<String, String> keys = new HashMap<>();
  private final Map<String, String> erasures = new HashMap<>();

  /** Declares {@code parameters} as the type variables of {@code scope}, a prefix such as "C". */
  ApiTypeRefs with(final String scope, final List<TypeParameter> parameters) {
    final ApiTypeRefs next = new ApiTypeRefs();
    next.keys.putAll(keys);
    next.erasures.putAll(erasures);
    for (int index = 0; index < parameters.size(); index++) {
      final TypeParameter parameter = parameters.get(index);
      next.keys.put(parameter.name(), "#" + scope + index);
      next.erasures.put(parameter.name(), OBJECT);
    }
    for (final TypeParameter parameter : parameters) {
      if (!parameter.bounds().isEmpty()) {
        next.erasures.put(parameter.name(), next.of(parameter.bounds().getFirst()).erasure());
      }
    }
    return next;
  }

  /** Returns the API form of the declared type parameters. */
  List<ApiTypeParameter> parametersOf(final List<TypeParameter> parameters, final String variance) {
    final List<ApiTypeParameter> found = new ArrayList<>();
    for (final TypeParameter parameter : parameters) {
      found.add(
          new ApiTypeParameter(
              parameter.name(), parameter.bounds().stream().map(this::of).toList(), variance));
    }
    return found;
  }

  /** Returns the API form of {@code type}. */
  ApiTypeRef of(final JType type) {
    return new ApiTypeRef(text(type), key(type), erasure(type), false);
  }

  /** Returns the API form of a binary class name. */
  static ApiTypeRef named(final String binaryName) {
    return ApiTypeRef.of(source(binaryName));
  }

  static String source(final String binaryName) {
    return binaryName.replace('$', '.');
  }

  private String text(final JType type) {
    return switch (type) {
      case JType.Primitive primitive -> primitive.name();
      case JType.ClassType named -> source(named.name()) + arguments(named, this::text);
      case JType.ArrayType array -> text(array.component()) + ARRAY;
      case JType.TypeVariable variable -> variable.name();
      case JType.Wildcard wildcard -> wildcard(wildcard, this::text);
    };
  }

  private String key(final JType type) {
    return switch (type) {
      case JType.Primitive primitive -> primitive.name();
      case JType.ClassType named -> source(named.name()) + arguments(named, this::key);
      case JType.ArrayType array -> key(array.component()) + ARRAY;
      case JType.TypeVariable variable -> keys.getOrDefault(variable.name(), variable.name());
      case JType.Wildcard wildcard -> wildcard(wildcard, this::key);
    };
  }

  private String erasure(final JType type) {
    return switch (type) {
      case JType.Primitive primitive -> primitive.name();
      case JType.ClassType named -> source(named.name());
      case JType.ArrayType array -> erasure(array.component()) + ARRAY;
      case JType.TypeVariable variable -> erasures.getOrDefault(variable.name(), OBJECT);
      case JType.Wildcard wildcard -> OBJECT;
    };
  }

  private static String arguments(
      final JType.ClassType type, final java.util.function.Function<JType, String> writer) {
    return type.arguments().isEmpty()
        ? ""
        : type.arguments().stream().map(writer).collect(Collectors.joining(SEPARATOR, "<", ">"));
  }

  private static String wildcard(
      final JType.Wildcard wildcard, final java.util.function.Function<JType, String> writer) {
    if (wildcard.bound() == null) {
      return "?";
    }
    return (wildcard.upper() ? "? extends " : "? super ") + writer.apply(wildcard.bound());
  }
}
