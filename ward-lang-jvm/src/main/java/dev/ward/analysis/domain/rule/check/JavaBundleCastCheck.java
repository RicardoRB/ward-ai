package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a cast of {@code Bundle.getSerializable(...)} to a subtype of {@code Map}, {@code List},
 * {@code CharSequence} or {@code SparseArray} (or an array of a {@code CharSequence} class other
 * than {@code String}), as Error Prone's BundleDeserializationCast does: the bundle may hand back
 * the base type. The checks run in Error Prone's order, and a type whose supertypes are not known
 * is only decided where its own name settles it.
 */
public final class JavaBundleCastCheck implements RuleCheck {

  private static final String STRING = "java.lang.String";
  private static final String MAP = "java.util.Map";
  private static final String LIST = "java.util.List";
  private static final String CHAR_SEQUENCE = "java.lang.CharSequence";
  private static final String SPARSE = "android.util.SparseArray";
  private static final Set<String> CHAR_SEQUENCE_CLASSES =
      Set.of("java.lang.StringBuilder", "java.lang.StringBuffer", "java.nio.CharBuffer");
  private static final int CAST_PARTS = 4;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the type, {@code {max}} its base type
   */
  public JavaBundleCastCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.CALLS_AND_TYPES;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      final SyntaxNode cast = visit.node();
      if (isBundleCast(cast, types)) {
        types
            .typeOf(cast.span())
            .flatMap(type -> base(type, ancestors(cast, types)))
            .ifPresent(
                base ->
                    matches.add(
                        CheckSupport.at(
                            cast.span(),
                            CheckSupport.fill(
                                message, types.typeOf(cast.span()).get().name(), base))));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isBundleCast(final SyntaxNode cast, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = cast.children();
    return parts.size() == CAST_PARTS
        && "'('".equals(parts.get(0).kind())
        && "typeType".equals(parts.get(1).kind())
        && CallSite.of(parts.get(CAST_PARTS - 1), types)
            .filter(call -> call.is("android.os.Bundle", "getSerializable", false))
            .isPresent();
  }

  private static Optional<Set<String>> ancestors(final SyntaxNode cast, final UnitTypeFacts types) {
    return types
        .membersOf(cast.span())
        .map(
            members -> {
              final Set<String> names = new HashSet<>();
              members.ancestors().forEach(ancestor -> names.add(ancestor.name()));
              return names;
            });
  }

  /** Returns the base type to cast to, when the cast target is one of the flattened subtypes. */
  private static Optional<String> base(final TypeRef type, final Optional<Set<String>> known) {
    final Set<String> supers = known.orElse(Set.of());
    final String name = type.name();
    if (type.primitive() || type.arrayDepth() > 0) {
      return arrayBase(type);
    }
    if (STRING.equals(name)) {
      return Optional.empty();
    }
    if (isA(name, supers, MAP)) {
      return exact(name, Set.of(MAP, "java.util.HashMap"), "Map");
    }
    if (known.isEmpty() || supers.contains("android.os.Parcelable")) {
      return Optional.empty();
    }
    return flattened(name, supers);
  }

  private static Optional<String> flattened(final String name, final Set<String> supers) {
    if (isA(name, supers, CHAR_SEQUENCE)) {
      return exact(name, Set.of(CHAR_SEQUENCE), "CharSequence");
    }
    if (isA(name, supers, LIST)) {
      return exact(name, Set.of(LIST, "java.util.ArrayList"), "List");
    }
    return isA(name, supers, SPARSE)
        ? exact(name, Set.of(SPARSE), "SparseArray")
        : Optional.empty();
  }

  private static Optional<String> arrayBase(final TypeRef type) {
    final boolean charSequences =
        type.arrayDepth() == 1 && CHAR_SEQUENCE_CLASSES.contains(type.name());
    return charSequences ? Optional.of("CharSequence[]") : Optional.empty();
  }

  private static boolean isA(final String name, final Set<String> supers, final String base) {
    return base.equals(name) || supers.contains(base);
  }

  private static Optional<String> exact(
      final String name, final Set<String> allowed, final String base) {
    return allowed.contains(name) ? Optional.empty() : Optional.of(base);
  }
}
