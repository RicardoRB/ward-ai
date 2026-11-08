package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.types.TypeRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** Compares a {@code java.util.Formatter} format string with the arguments given for it. */
final class FormatSpecifiers {

  private static final Pattern SPEC =
      Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)(\\d+)?(\\.\\d+)?([tT]?[a-zA-Z%])");
  private static final String VALID = "bhscdoxefgat";
  private static final Set<String> INTEGRAL = Set.of("d", "o", "x");
  private static final Set<String> DECIMAL = Set.of("e", "f", "g", "a");
  private static final Set<String> NOT_INTEGRAL =
      Set.of(
          "java.lang.String",
          "boolean",
          "java.lang.Boolean",
          "double",
          "float",
          "java.lang.Double",
          "java.lang.Float",
          "char",
          "java.lang.Character");
  private static final Set<String> NOT_DECIMAL =
      Set.of(
          "java.lang.String",
          "int",
          "long",
          "short",
          "byte",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Short",
          "java.lang.Byte",
          "boolean",
          "java.lang.Boolean",
          "char",
          "java.lang.Character");
  private static final int INDEX_GROUP = 1;
  private static final int FLAGS_GROUP = 2;
  private static final int CONVERSION_GROUP = 5;

  private FormatSpecifiers() {}

  /** One conversion of a format; {@code index} is its explicit argument index, 0 for none. */
  private record Spec(String conversion, int index, boolean relative) {

    boolean sequential() {
      return index == 0 && !relative;
    }
  }

  /**
   * Returns the first fault of the format against its arguments, if there is one.
   *
   * @param format the content of the format string literal, with escapes as written
   * @param types the type of each argument, empty when unknown
   */
  static Optional<String> fault(final String format, final List<Optional<TypeRef>> types) {
    final List<Spec> specs = specifiers(format);
    return unknown(specs).or(() -> mismatches(specs, types)).or(() -> count(specs, types.size()));
  }

  private static List<Spec> specifiers(final String format) {
    final List<Spec> specs = new ArrayList<>();
    final Matcher spec = SPEC.matcher(format);
    while (spec.find()) {
      final String conversion = spec.group(CONVERSION_GROUP);
      if (!"%".equals(conversion) && !"n".equals(conversion)) {
        final String explicit = spec.group(INDEX_GROUP);
        final int index =
            explicit == null ? 0 : Integer.parseInt(explicit.substring(0, explicit.length() - 1));
        specs.add(new Spec(conversion, index, spec.group(FLAGS_GROUP).contains("<")));
      }
    }
    return specs;
  }

  private static Optional<String> unknown(final List<Spec> specs) {
    return specs.stream()
        .map(Spec::conversion)
        .filter(c -> c.length() == 1 && VALID.indexOf(c.toLowerCase(Locale.ROOT)) < 0)
        .findFirst()
        .map(c -> "the conversion %" + c + " does not exist");
  }

  private static Optional<String> mismatches(
      final List<Spec> specs, final List<Optional<TypeRef>> types) {
    final List<String> sequential =
        specs.stream().filter(Spec::sequential).map(Spec::conversion).toList();
    return IntStream.range(0, sequential.size())
        .mapToObj(index -> mismatch(sequential.get(index), index, types))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private static Optional<String> count(final List<Spec> specs, final int given) {
    final long sequential = specs.stream().filter(Spec::sequential).count();
    if (sequential < specs.size()) {
      final int highest = specs.stream().mapToInt(Spec::index).max().orElse(0);
      return highest > given
          ? Optional.of("the format refers to a missing argument")
          : Optional.empty();
    }
    if (sequential > given) {
      return Optional.of(
          "the format needs " + sequential + " arguments but " + given + " are given");
    }
    return sequential < given
        ? Optional.of("the format uses " + sequential + " arguments but " + given + " are given")
        : Optional.empty();
  }

  private static Optional<String> mismatch(
      final String conversion, final int index, final List<Optional<TypeRef>> types) {
    if (index >= types.size() || types.get(index).isEmpty()) {
      return Optional.empty();
    }
    final TypeRef type = types.get(index).get();
    final String shown = type.arrayDepth() == 0 ? type.name() : "";
    final String lower = conversion.toLowerCase(Locale.ROOT);
    final boolean bad =
        INTEGRAL.contains(lower) && NOT_INTEGRAL.contains(shown)
            || DECIMAL.contains(lower) && NOT_DECIMAL.contains(shown);
    return bad
        ? Optional.of("the conversion %" + conversion + " does not accept a " + shown)
        : Optional.empty();
  }
}
