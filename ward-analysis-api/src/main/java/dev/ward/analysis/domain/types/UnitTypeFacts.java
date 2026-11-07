package dev.ward.analysis.domain.types;

import dev.ward.analysis.domain.ir.Span;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The type facts of one source unit, immutable, as the pre-pass fetched them. Every query takes the
 * span of a syntax node; a span with no answer yields an empty result or {@link Nullness#UNKNOWN}.
 * Lists such as annotations, overridden methods and thrown exceptions are read from {@link
 * #at(Span)}. Facts are looked up by {@link #key(Span)}, then by {@link #startKey(Span)}, so
 * fixtures can name a fact by the position where a node starts.
 */
public final class UnitTypeFacts {
  private static final UnitTypeFacts NONE = new UnitTypeFacts(Map.of());

  private final Map<String, SpanFacts> facts;

  /**
   * Creates the facts from {@code facts}, keyed by {@link #key(Span)} or {@link #startKey(Span)}.
   */
  public UnitTypeFacts(final Map<String, SpanFacts> facts) {
    this.facts = Map.copyOf(Objects.requireNonNull(facts, "facts"));
  }

  /** Returns the facts of a unit that no resolver answered. */
  public static UnitTypeFacts none() {
    return NONE;
  }

  /** Returns the key of an exact span: {@code startLine:startColumn-endLine:endColumn}. */
  public static String key(final Span span) {
    return startKey(span) + "-" + span.endLine() + ":" + span.endColumn();
  }

  /** Returns the key of every span starting at the same position: {@code line:column}. */
  public static String startKey(final Span span) {
    return span.startLine() + ":" + span.startColumn();
  }

  /** Returns everything known about {@code span}. */
  public SpanFacts at(final Span span) {
    final SpanFacts exact = facts.get(key(span));
    return exact != null ? exact : facts.getOrDefault(startKey(span), SpanFacts.UNKNOWN);
  }

  /** Returns the type of the expression at {@code span}. */
  public Optional<TypeRef> typeOf(final Span span) {
    return Optional.ofNullable(at(span).type());
  }

  /** Returns the shape of the type declared or typed at {@code span}. */
  public Optional<TypeShape> shapeOf(final Span span) {
    return Optional.ofNullable(at(span).shape());
  }

  /** Returns the target of the invocation at {@code span}. */
  public Optional<CallTarget> callTarget(final Span span) {
    return Optional.ofNullable(at(span).call());
  }

  /** Returns the nullness of the declaration at {@code span}, from its annotations and scopes. */
  public Nullness nullness(final Span span) {
    final SpanFacts known = at(span);
    return NullnessAnnotations.of(known.annotations(), known.scopes());
  }

  /** Returns the compile-time constant value of the expression at {@code span}. */
  public Optional<ConstantValue> constant(final Span span) {
    return Optional.ofNullable(at(span).constant());
  }
}
