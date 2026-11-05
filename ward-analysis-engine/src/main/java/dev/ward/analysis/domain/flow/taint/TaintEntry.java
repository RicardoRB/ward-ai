package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One entry of a taint specification: a kind of site, the name that site must have, and optionally
 * the receiver type or the qualifier text that it must have too.
 *
 * <p>A parameter entry matches a parameter that carries an annotation of the same name, so its
 * {@code name} is the annotation. A sink entry names the argument positions it guards.
 *
 * @param kind the kind of site the entry matches
 * @param name the called name, the member name, or the annotation of a parameter
 * @param receiver the receiver type the site must have, when the entry names one
 * @param qualifier the qualifier text the site must have, when the entry names one
 * @param arguments the argument positions a sink guards; empty for other entries
 */
public record TaintEntry(
    FlowSiteKind kind,
    String name,
    Optional<String> receiver,
    Optional<String> qualifier,
    List<Integer> arguments) {

  /**
   * Copies the arguments and validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public TaintEntry {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(receiver, "receiver");
    Objects.requireNonNull(qualifier, "qualifier");
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
  }

  /**
   * Reports whether the entry matches a site. The receiver type decides when the site has one. A
   * site without one matches only a receiver entry that also names the qualifier, and the text of
   * the qualifier must then be equal, so unresolved receivers never match by accident.
   *
   * @param site the site to test
   * @return true when the kind, the name and the receiver or qualifier agree
   */
  public boolean matches(final FlowSite site) {
    if (site.kind() != kind) {
      return false;
    }
    if (kind == FlowSiteKind.PARAMETER) {
      return site.annotations().stream().anyMatch(name::equals);
    }
    return name.equals(site.name()) && receiverMatches(site);
  }

  private boolean receiverMatches(final FlowSite site) {
    if (receiver.isPresent()) {
      return site.receiverType()
          .map(receiver.get()::equals)
          .orElseGet(() -> qualifier.isPresent() && qualifier.get().equals(site.qualifier()));
    }
    return qualifier.map(site.qualifier()::equals).orElse(true);
  }
}
