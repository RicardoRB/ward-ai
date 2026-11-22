package dev.ward.infrastructure.dependency.dotnet;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.Map;

/** Reads the untyped JSON of the NuGet lock and assets files. */
final class NugetJson {
  private NugetJson() {}

  static Map<?, ?> root(final String text, final JsonCodec codec) {
    final Object parsed;
    try {
      parsed = codec.fromJson(text, Object.class);
    } catch (JsonCodecException | IllegalArgumentException e) {
      throw new LockfileFormatException("it is not valid JSON", e);
    }
    if (parsed instanceof Map<?, ?> root) {
      return root;
    }
    throw new LockfileFormatException("it is not a JSON object");
  }

  static Map<?, ?> map(final Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  static String text(final Object value) {
    return value instanceof String text ? text : "";
  }
}
