package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The {@code crap} gate init offers: it has no command, and it needs the gate that produces the
 * coverage data placed before it.
 */
public final class CrapInit {
  /** Why init does not offer the gate for a toolchain that cannot write its report. */
  public static final String MANUAL_STEP =
      "The crap gate is not available for this project: init cannot write its coverage report."
          + " Add a [checks.crap] table with uses = \"crap\" and a report path to ward.toml by"
          + " hand.";

  private CrapInit() {}

  /** Returns the opt-in gate choice; it runs no command, so it is always available. */
  public static InitGateOption option() {
    return new InitGateOption(
        SupportedQualityGates.CRAP,
        "Fails functions whose CRAP score (complexity and untested lines) is too high (opt-in).",
        List.of(),
        true,
        "Reads the coverage report that init configures; it runs no command.");
  }

  /**
   * Returns {@code gates} with {@code extra} and then {@code crap} placed after the gate {@code
   * needs} and after {@code alsoAfter}, whichever of them comes last; {@code settings} go on the
   * gate. A selection without {@code crap} is returned as it is.
   *
   * @throws WardException if {@code crap} is selected and {@code needs} is not
   */
  public static List<InitGateOption> place(
      final List<InitGateOption> gates,
      final String needs,
      final List<String> alsoAfter,
      final List<InitGateOption> extra,
      final Map<String, String> settings) {
    final Optional<InitGateOption> found =
        gates.stream().filter(gate -> SupportedQualityGates.CRAP.equals(gate.id())).findFirst();
    if (found.isEmpty()) {
      return gates;
    }
    final InitGateOption crap = found.orElseThrow();
    final List<InitGateOption> rest = new ArrayList<>(gates);
    rest.remove(crap);
    if (indexOf(rest, needs) < 0) {
      throw new WardException(
          "The crap gate needs the '"
              + needs
              + "' gate, which produces its coverage data. Select both gates.");
    }
    final int after =
        Stream.concat(Stream.of(needs), alsoAfter.stream())
                .mapToInt(id -> indexOf(rest, id))
                .max()
                .orElseThrow()
            + 1;
    final List<InitGateOption> placed = new ArrayList<>(rest.subList(0, after));
    placed.addAll(extra);
    placed.add(
        new InitGateOption(
            crap.id(),
            crap.description(),
            crap.command(),
            true,
            crap.availabilityMessage(),
            settings));
    placed.addAll(rest.subList(after, rest.size()));
    return List.copyOf(placed);
  }

  private static int indexOf(final List<InitGateOption> gates, final String id) {
    return IntStream.range(0, gates.size())
        .filter(index -> gates.get(index).id().equals(id))
        .findFirst()
        .orElse(-1);
  }
}
