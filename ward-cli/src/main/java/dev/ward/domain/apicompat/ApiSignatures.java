package dev.ward.domain.apicompat;

import dev.ward.analysis.domain.api.ApiParameter;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.api.ApiTypeRef;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Writes and compares the parts of signatures that the comparison reports on. */
final class ApiSignatures {
  private ApiSignatures() {}

  /** Returns the type as a signature, such as {@code public final class p.A<T>}. */
  static String of(final ApiType type) {
    final StringBuilder text = new StringBuilder(64);
    text.append(type.visibility().name().toLowerCase(Locale.ROOT)).append(' ');
    if (type.isFinal() && type.kind() != ApiType.Kind.ENUM && type.kind() != ApiType.Kind.RECORD) {
      text.append("final ");
    }
    if (type.isSealed()) {
      text.append("sealed ");
    }
    if (type.isAbstract() && type.kind() == ApiType.Kind.CLASS) {
      text.append("abstract ");
    }
    text.append(type.kind().name().toLowerCase(Locale.ROOT)).append(' ').append(type.name());
    if (!type.typeParameters().isEmpty()) {
      text.append(
          type.typeParameters().stream()
              .map(ApiSignatures::parameter)
              .collect(Collectors.joining(", ", "<", ">")));
    }
    return text.toString();
  }

  private static String parameter(final ApiTypeParameter parameter) {
    final String variance = parameter.variance().isEmpty() ? "" : parameter.variance() + " ";
    final String bounds =
        parameter.bounds().isEmpty()
            ? ""
            : " extends "
                + parameter.bounds().stream()
                    .map(ApiTypeRef::text)
                    .collect(Collectors.joining(" & "));
    return variance + parameter.name() + bounds;
  }

  /** Returns whether the type parameter lists differ in count, bounds or variance. */
  static boolean typeParametersDiffer(
      final List<ApiTypeParameter> before, final List<ApiTypeParameter> after) {
    return !shape(before).equals(shape(after));
  }

  private static List<String> shape(final List<ApiTypeParameter> parameters) {
    return parameters.stream()
        .map(
            parameter ->
                parameter.variance()
                    + ":"
                    + parameter.bounds().stream()
                        .map(ApiTypeRef::key)
                        .collect(Collectors.joining("&")))
        .toList();
  }

  /** Returns the parameter types of a method, as the keys the comparison uses. */
  static List<String> parameterKeys(final List<ApiParameter> parameters) {
    return parameters.stream().map(parameter -> parameter.type().key()).toList();
  }
}
