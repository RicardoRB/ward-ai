package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where one type qualifier is declared on the methods and fields of a file: the sources a value can
 * come from and the sinks that require a qualifier. A method or field of another file is unknown,
 * so it is neither.
 *
 * @param types the type facts of the file
 * @param scope the qualifiers of the file and where it applies them
 * @param qualifier the qualifier being checked
 * @param type the simple name of the type that declares the body
 * @param method what the body declares for the qualifier, or null
 */
record QualSites(
    UnitTypeFacts types,
    QualifierScope scope,
    QualifierScope.Qualifier qualifier,
    String type,
    QualifierScope.Method method) {

  private static final Pattern NAME = Pattern.compile("(?:this\\.)?([A-Za-z_$][A-Za-z0-9_$]*)");

  /** Returns what the callee of {@code call} requires of argument {@code index}. */
  Optional<QualWhen> parameterNeed(final NullEvent call, final int index) {
    return callee(call).flatMap(found -> parameter(found, index));
  }

  private Optional<QualWhen> parameter(final QualifierScope.Method found, final int index) {
    final List<Map<String, QualWhen>> all = found.parameters();
    return index < all.size()
        ? Optional.ofNullable(all.get(index).get(qualifier.name()))
        : Optional.empty();
  }

  /** Returns what the body requires of the value it returns. */
  Optional<QualWhen> returnNeed() {
    return method == null
        ? Optional.empty()
        : Optional.ofNullable(method.returns().get(qualifier.name()));
  }

  /** Returns what the field requires of the value stored into it. */
  Optional<QualWhen> fieldNeed(final String field) {
    return Optional.ofNullable(scope.fields().get(type + "#" + field))
        .map(declared -> declared.get(qualifier.name()));
  }

  /** Returns the source of the value of {@code operand} when it is a call or field of the file. */
  Optional<QualState.Source> sourceOf(final NullEvent.Operand operand) {
    if (operand.node() == null || operand.variable() != null) {
      return Optional.empty();
    }
    final Optional<QualState.Source> returned =
        types
            .callTarget(operand.node().span())
            .flatMap(this::declared)
            .map(
                found ->
                    source(QualState.Origin.RETURN_VALUE, found.returns().get(qualifier.name())));
    return returned.isPresent() ? returned : field(operand);
  }

  private Optional<QualState.Source> field(final NullEvent.Operand operand) {
    final java.util.regex.Matcher matcher = NAME.matcher(SyntaxWalk.text(operand.node()));
    final String key = matcher.matches() ? type + "#" + matcher.group(1) : "";
    return scope.fields().containsKey(key)
        ? Optional.of(
            source(QualState.Origin.FIELD_LOAD, scope.fields().get(key).get(qualifier.name())))
        : Optional.empty();
  }

  private Optional<QualifierScope.Method> callee(final NullEvent call) {
    return types.callTarget(call.node().span()).flatMap(this::declared);
  }

  private Optional<QualifierScope.Method> declared(final CallTarget target) {
    final String key =
        NullScope.key(target.declaringType().name(), target.name(), target.parameters().size());
    return Optional.ofNullable(scope.methods().get(key));
  }

  /** Returns the source a declaration makes: its annotation, or unknown when it has none. */
  static QualState.Source source(final QualState.Origin origin, final QualWhen declared) {
    return new QualState.Source(
        origin, declared == null ? QualWhen.UNKNOWN : declared, declared != null);
  }

  /** Tells whether a source is tracked: an unknown value only counts for a strict qualifier. */
  boolean tracked(final QualState.Source source) {
    return source.when() != QualWhen.UNKNOWN || qualifier.strict();
  }
}
