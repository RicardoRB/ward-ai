package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the fact objects of the sidecar protocol, which {@code types.json} fixtures share. Each
 * fact is optional; a field of the wrong shape rejects the whole object, so a broken sidecar is
 * never taken for an answer.
 */
final class TypeFactsJson {
  private TypeFactsJson() {}

  /** Reads a map of key to fact object. */
  static Map<String, SpanFacts> spanFacts(final Map<?, ?> facts) {
    final Map<String, SpanFacts> result = new LinkedHashMap<>();
    facts.forEach(
        (key, value) -> result.put(JsonValues.string(key), spanFact(JsonValues.object(value))));
    return result;
  }

  private static SpanFacts spanFact(final Map<?, ?> fact) {
    return new SpanFacts(
        fact.containsKey("type") ? typeRef(fact.get("type")) : null,
        fact.containsKey("shape") ? shape(JsonValues.object(fact.get("shape"))) : null,
        fact.containsKey("call") ? call(fact.get("call")) : null,
        JsonValues.strings(fact.get("annotations")),
        JsonValues.strings(fact.get("scopes")),
        JsonValues.list(fact.get("overrides")).stream().map(TypeFactsJson::call).toList(),
        JsonValues.list(fact.get("thrown")).stream().map(TypeFactsJson::typeRef).toList(),
        fact.containsKey("constant") ? new ConstantValue(fact.get("constant")) : null);
  }

  private static TypeShape shape(final Map<?, ?> shape) {
    return new TypeShape(
        TypeShape.Kind.valueOf(JsonValues.string(shape.get("kind")).toUpperCase(Locale.ROOT)),
        JsonValues.flag(shape.get("abstract")),
        JsonValues.flag(shape.get("final")),
        JsonValues.flag(shape.get("sealed")),
        JsonValues.list(shape.get("supertypes")).stream().map(TypeFactsJson::typeRef).toList());
  }

  private static CallTarget call(final Object value) {
    final Map<?, ?> call = JsonValues.object(value);
    return new CallTarget(
        typeRef(call.get("declaring")),
        JsonValues.string(call.get("name")),
        JsonValues.list(call.get("params")).stream().map(TypeFactsJson::typeRef).toList(),
        call.containsKey("returns") ? typeRef(call.get("returns")) : null,
        JsonValues.flag(call.get("static")),
        JsonValues.flag(call.get("varargs")));
  }

  private static TypeRef typeRef(final Object value) {
    final Map<?, ?> type = JsonValues.object(value);
    final Object depth = type.get("array");
    return new TypeRef(
        JsonValues.string(type.get("name")),
        JsonValues.list(type.get("args")).stream().map(TypeFactsJson::typeRef).toList(),
        depth == null ? 0 : Math.toIntExact(JsonValues.number(depth)),
        JsonValues.flag(type.get("primitive")));
  }
}
