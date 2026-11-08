package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the fact objects of the native resolver protocol, which {@code types.json} fixtures share.
 * Each fact is optional; a field of the wrong shape rejects the whole object, so a broken resolver
 * is never taken for an answer.
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
        fact.containsKey("constant") ? new ConstantValue(fact.get("constant")) : null,
        fact.containsKey("members") ? members(JsonValues.object(fact.get("members"))) : null);
  }

  private static TypeMembers members(final Map<?, ?> members) {
    return new TypeMembers(
        JsonValues.list(members.get("methods")).stream().map(TypeFactsJson::call).toList(),
        JsonValues.list(members.get("fields")).stream().map(TypeFactsJson::field).toList(),
        JsonValues.list(members.get("ancestors")).stream().map(TypeFactsJson::typeRef).toList(),
        JsonValues.strings(members.get("annotations")),
        JsonValues.list(members.get("inheritedMethods")).stream().map(TypeFactsJson::call).toList(),
        JsonValues.list(members.get("inheritedFields")).stream().map(TypeFactsJson::field).toList(),
        values(members.get("annotationValues")));
  }

  /** Reads {@code {"<annotation>#<element>": [values]}}; a single value may be written bare. */
  private static Map<String, List<String>> values(final Object value) {
    final Map<String, List<String>> values = new LinkedHashMap<>();
    if (value != null) {
      JsonValues.object(value)
          .forEach(
              (key, list) ->
                  values.put(
                      JsonValues.string(key),
                      list instanceof List<?>
                          ? JsonValues.strings(list)
                          : List.of(JsonValues.string(list))));
    }
    return values;
  }

  private static FieldRef field(final Object value) {
    final Map<?, ?> field = JsonValues.object(value);
    return new FieldRef(
        JsonValues.string(field.get("name")),
        field.containsKey("type") ? typeRef(field.get("type")) : null,
        JsonValues.flag(field.get("static")),
        JsonValues.strings(field.get("annotations")));
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
        JsonValues.flag(call.get("varargs")),
        JsonValues.strings(call.get("annotations")),
        JsonValues.list(call.get("paramAnnotations")).stream().map(JsonValues::strings).toList(),
        JsonValues.strings(call.get("declaringAnnotations")),
        JsonValues.strings(call.get("compatibleWith")),
        JsonValues.strings(call.get("typeParameters")),
        JsonValues.strings(call.get("declaringTypeParameters")),
        call.containsKey("final") ? JsonValues.flag(call.get("final")) : null,
        values(call.get("annotationValues")),
        values(call.get("declaringAnnotationValues")),
        call.containsKey("abstract") ? JsonValues.flag(call.get("abstract")) : null);
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
