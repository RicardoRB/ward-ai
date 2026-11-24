package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;

/**
 * The uses of a value that a validator proved: the name of the site, {@code
 * <what>:<function>@<index>}, and the entry of a specification that trusts them.
 */
final class ValidatedUses {
  private static final String ARITY = "ward:arity:";
  private static final char FUNCTION_START = ':';
  private static final char INDEX_START = '@';

  private ValidatedUses() {}

  /** The name of the validator that a use names, without what it proves and the index. */
  static String function(final FlowSite site) {
    final String name = site.name();
    return name.substring(name.indexOf(FUNCTION_START) + 1, name.lastIndexOf(INDEX_START));
  }

  /** The number of parameters of the validator that a use names. */
  static int arity(final FlowSite site) {
    return site.annotations().stream()
        .filter(annotation -> annotation.startsWith(ARITY))
        .mapToInt(annotation -> Integer.parseInt(annotation.substring(ARITY.length())))
        .findFirst()
        .orElse(-1);
  }

  /** The name of the use of an argument that a validator of {@code what} proves. */
  static String name(final String what, final String function, final int index) {
    return what + FUNCTION_START + function + INDEX_START + index;
  }

  /** The entry that trusts the uses of an argument that the method validates for {@code what}. */
  static TaintEntry use(
      final LocalMethod method,
      final String qualifier,
      final String what,
      final int index
  ) {
    return new TaintEntry(
        FlowSiteKind.VALIDATED,
        name(what, method.name(), index),
        Optional.empty(),
        Optional.of(qualifier),
        List.of()
    );
  }
}
