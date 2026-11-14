package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One entry of a taint specification: a kind of site, the name that site must have, and optionally
 * the receiver type or the qualifier text that it must have too.
 *
 * <p>A parameter entry matches a parameter that carries an annotation of the same name, so its
 * {@code name} is the annotation. A sink entry names the argument positions it guards. A string
 * entry matches a string-building site whose literal text matches its {@code pattern}, and guards
 * every value part of that site. A literal entry matches a literal whose text matches its {@code
 * pattern}.
 *
 * @param kind the kind of site the entry matches
 * @param name the called name, the member name, or the annotation of a parameter
 * @param receiver the receiver type the site must have, when the entry names one
 * @param qualifier the qualifier text the site must have, when the entry names one; a text that
 *     starts with {@code *} matches any qualifier that ends with the rest, such as {@code
 *     *.environment()}
 * @param arguments the argument positions a sink guards; empty for other entries
 * @param pattern the literal-text pattern of a string entry, matched with {@code find}
 */
public record TaintEntry(
    FlowSiteKind kind,
    String name,
    Optional<String> receiver,
    Optional<String> qualifier,
    List<Integer> arguments,
    Optional<Pattern> pattern) {

  private static final String ANY_PREFIX = "*";

  /**
   * Creates an entry that has no literal-text pattern.
   *
   * @param kind the kind of site the entry matches
   * @param name the called name, the member name, or the annotation of a parameter
   * @param receiver the receiver type the site must have, when the entry names one
   * @param qualifier the qualifier text the site must have, when the entry names one; a text that
   *     starts with {@code *} matches any qualifier that ends with the rest, such as {@code
   *     *.environment()}
   * @param arguments the argument positions a sink guards; empty for other entries
   */
  public TaintEntry(
      final FlowSiteKind kind,
      final String name,
      final Optional<String> receiver,
      final Optional<String> qualifier,
      final List<Integer> arguments) {
    this(kind, name, receiver, qualifier, arguments, Optional.empty());
  }

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
    Objects.requireNonNull(pattern, "pattern");
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
    if (kind == FlowSiteKind.STRING_BUILD) {
      return pattern.map(p -> p.matcher(site.name()).find()).orElse(false);
    }
    if (kind == FlowSiteKind.LITERAL) {
      return pattern.map(p -> p.matcher(site.name()).find()).orElse(true);
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
          .orElseGet(() -> qualifier.isPresent() && qualifierMatches(qualifier.get(), site));
    }
    return qualifier.map(text -> qualifierMatches(text, site)).orElse(true);
  }

  /** A qualifier that starts with {@code *} matches any qualifier that ends with the rest. */
  private static boolean qualifierMatches(final String expected, final FlowSite site) {
    return expected.startsWith(ANY_PREFIX)
        ? site.qualifier().endsWith(expected.substring(ANY_PREFIX.length()))
        : expected.equals(site.qualifier());
  }
}
