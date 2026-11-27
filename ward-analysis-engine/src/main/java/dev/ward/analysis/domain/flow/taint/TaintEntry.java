package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.Arrays;
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
 * <p>A parameter entry whose {@code qualifier} is {@code getters} also makes the getters of the
 * parameter's type sources wherever the project reads them, as an assumed flow (see {@code
 * BoundGetters}). A call entry whose {@code name} ends with {@code *} matches a call without
 * arguments whose name starts with the rest and is longer than it.
 *
 * @param kind the kind of site the entry matches
 * @param name the called name, the member name, or the annotation of a parameter
 * @param receiver the receiver type the site must have, when the entry names one
 * @param qualifier the qualifier text the site must have, when the entry names one; a text that
 *     starts with {@code *} matches any qualifier that ends with the rest, such as {@code
 *     *.environment()}
 * @param arguments the argument positions a sink guards; empty for other entries
 * @param pattern the literal-text pattern of a string entry, matched with {@code find}
 * @param exceptCalls for a sink: the calls, written {@code Qualifier.name}, whose result is a safe
 *     value when it is the whole guarded argument, such as {@code JSON.stringify}
 * @param assumptions for an entry that the analysis derived from a method body: the steps of that
 *     body's flow that it assumed rather than proved; empty for the entries of a specification file
 */
public record TaintEntry(
    FlowSiteKind kind,
    String name,
    Optional<String> receiver,
    Optional<String> qualifier,
    List<Integer> arguments,
    Optional<Pattern> pattern,
    List<String> exceptCalls,
    List<String> assumptions
) {
  private static final String ANY_PREFIX = "*";
  /** A qualifier that matches only a call written without a receiver, such as {@code exec(x)}. */
  private static final String UNQUALIFIED = "<none>";
  /** The {@code except-calls} marker of a sink that a plain-text, {@code nosniff} response makes safe. */
  static final String TEXT_RESPONSE = "<text-response>";
  private static final Pattern CHAIN_SEPARATOR = Pattern.compile("\\??\\.");

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
      final List<Integer> arguments
  ) {
    this(kind, name, receiver, qualifier, arguments, Optional.empty(), List.of(), List.of());
  }

  /**
   * Returns the entry with the assumptions of the flow it was derived from.
   *
   * @param assumed the assumed steps, sorted
   * @return the entry with those assumptions
   */
  public TaintEntry assuming(final List<String> assumed) {
    return new TaintEntry(
        kind,
        name,
        receiver,
        qualifier,
        arguments,
        pattern,
        exceptCalls,
        assumed
    );
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
    exceptCalls = List.copyOf(Objects.requireNonNull(exceptCalls, "exceptCalls"));
    assumptions = List.copyOf(Objects.requireNonNull(assumptions, "assumptions"));
  }

  /**
   * Reports whether the sink writes nothing that can run as markup when its response is typed as
   * plain text and carries {@code nosniff}: its {@code exceptCalls} hold {@value #TEXT_RESPONSE}.
   *
   * @return true when such a response makes every value of the sink harmless
   */
  public boolean safeInTextResponse() {
    return exceptCalls.contains(TEXT_RESPONSE);
  }

  /**
   * Reports whether a site is a call that this sink exempts: one of {@code exceptCalls}.
   *
   * @param site the call that is the whole guarded argument
   * @return true when the call is named by an {@code Qualifier.name} entry
   */
  public boolean exempts(final FlowSite site) {
    final String call = site.qualifier().isEmpty() ? site.name() : site.qualifier() + "." + site.name();
    return site.kind() == FlowSiteKind.CALL && exceptCalls.contains(call);
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
    return nameMatches(site) && receiverMatches(site);
  }

  private boolean nameMatches(final FlowSite site) {
    if (!name.endsWith(ANY_PREFIX)) {
      return name.equals(site.name());
    }
    final String prefix = name.substring(0, name.length() - ANY_PREFIX.length());
    return site.arguments().isEmpty()
        && site.name().startsWith(prefix)
        && site.name().length() > prefix.length();
  }

  private boolean receiverMatches(final FlowSite site) {
    if (receiver.isPresent()) {
      return site.receiverType()
          .map(receiver.get()::equals)
          .orElseGet(() -> qualifier.isPresent() && qualifierMatches(qualifier.get(), site));
    }
    return qualifier.map(text -> qualifierMatches(text, site)).orElse(true);
  }

  /**
   * A qualifier that starts with {@code *} matches any qualifier that ends with the rest. Any other
   * qualifier matches a receiver chain that has it as a member, at any depth: {@code db} and {@code
   * sequelize} both match {@code db.sequelize}.
   */
  private static boolean qualifierMatches(final String expected, final FlowSite site) {
    if (UNQUALIFIED.equals(expected)) {
      return site.qualifier().isEmpty();
    }
    return expected.startsWith(ANY_PREFIX)
        ? site.qualifier().endsWith(expected.substring(ANY_PREFIX.length()))
        : expected.equals(site.qualifier())
            || inChain(expected, site.qualifier());
  }

  /** Whether the chain, split at {@code .} and {@code ?.}, has the text as one of its members. */
  private static boolean inChain(final String expected, final String chain) {
    if (expected.isEmpty()) {
      return Arrays.asList(CHAIN_SEPARATOR.split(chain)).contains(expected);
    }
    int start = 0;
    while (start <= chain.length()) {
      final int dot = chain.indexOf('.', start);
      final int end = dot < 0 ? chain.length() : dot;
      final int last = dot > start && chain.charAt(dot - 1) == '?' ? dot - 1 : end;
      if (last - start == expected.length() && chain.startsWith(expected, start)) {
        return true;
      }
      if (dot < 0) {
        return false;
      }
      start = dot + 1;
    }
    return false;
  }
}
