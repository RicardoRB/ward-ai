package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code target="_blank"} on an {@code a} or {@code area} element with an {@code href} that
 * lacks {@code rel="noopener"}, as Biome does. A {@code noreferrer} counts when the option allows
 * it, an allowed domain and its subdomains are exempt, and a dynamic {@code rel} stays silent.
 */
public final class BiomeBlankTargetCheck implements RuleCheck {

  private static final Set<String> ELEMENTS = Set.of("a", "area");
  private static final String TARGET = "target";
  private static final String HREF = "href";
  private static final String REL = "rel";
  private static final String BLANK = "_blank";
  private static final String NOOPENER = "noopener";
  private static final String NOREFERRER = "noreferrer";
  private static final String SCHEME_SEPARATOR = "://";
  private static final String PROTOCOL_RELATIVE = "//";
  private static final String HOST_END = "/?#";
  private static final String DOT = ".";

  private final Set<String> allowDomains;
  private final boolean allowNoReferrer;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowDomains domains whose links are exempt, with their subdomains
   * @param allowNoReferrer whether {@code rel="noreferrer"} is enough
   * @param message the message; {@code {value}} is the target attribute's text
   */
  public BiomeBlankTargetCheck(
      final Set<String> allowDomains, final boolean allowNoReferrer, final String message) {
    this.allowDomains = Set.copyOf(allowDomains);
    this.allowNoReferrer = allowNoReferrer;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(JsxAttributes.OPENING, JsxAttributes.SELF_CLOSING))
        .stream()
        .filter(visit -> ELEMENTS.contains(JsxAttributes.elementName(visit.node())))
        .flatMap(visit -> violation(visit.node()).stream())
        .map(
            target ->
                CheckSupport.at(
                    target.span(), CheckSupport.fill(message, SyntaxWalk.text(target), "")))
        .toList();
  }

  /** The target attribute of a link that Biome reports, if the element is one. */
  private Optional<SyntaxNode> violation(final SyntaxNode element) {
    final List<SyntaxNode> attributes = JsxAttributes.of(element);
    final Optional<SyntaxNode> target = named(attributes, TARGET);
    final Optional<SyntaxNode> href = named(attributes, HREF);
    if (target.isEmpty() || href.isEmpty() || !isBlank(target.get())) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> rel = named(attributes, REL);
    if (rel.isPresent() && JsxAttributes.literal(rel.get()).isEmpty()) {
      return Optional.empty();
    }
    if (isAllowedHost(JsxAttributes.literal(href.get()))) {
      return Optional.empty();
    }
    final String relation =
        rel.flatMap(JsxAttributes::literal).orElse("").toLowerCase(Locale.ROOT);
    return relation.contains(NOOPENER) || (allowNoReferrer && relation.contains(NOREFERRER))
        ? Optional.empty()
        : target;
  }

  private static Optional<SyntaxNode> named(final List<SyntaxNode> attributes, final String name) {
    return attributes.stream()
        .filter(attribute -> name.equals(JsxAttributes.name(attribute)))
        .findFirst();
  }

  private static boolean isBlank(final SyntaxNode target) {
    return JsxAttributes.literal(target).filter(BLANK::equals).isPresent();
  }

  private boolean isAllowedHost(final Optional<String> href) {
    final String host = href.map(BiomeBlankTargetCheck::host).orElse("");
    return !host.isEmpty()
        && allowDomains.stream()
            .map(domain -> domain.toLowerCase(Locale.ROOT))
            .anyMatch(domain -> host.equals(domain) || host.endsWith(DOT + domain));
  }

  /** The lower-case host of an absolute or protocol-relative URL, or empty for a relative one. */
  private static String host(final String url) {
    final int start = url.indexOf(SCHEME_SEPARATOR);
    final String rest;
    if (start >= 0) {
      rest = url.substring(start + SCHEME_SEPARATOR.length());
    } else if (url.startsWith(PROTOCOL_RELATIVE)) {
      rest = url.substring(PROTOCOL_RELATIVE.length());
    } else {
      return "";
    }
    final int end = firstOf(rest, HOST_END);
    final String authority = end < 0 ? rest : rest.substring(0, end);
    final String withoutUser = authority.substring(authority.lastIndexOf('@') + 1);
    final int port = withoutUser.indexOf(':');
    final String host = port < 0 ? withoutUser : withoutUser.substring(0, port);
    return host.toLowerCase(Locale.ROOT);
  }

  private static int firstOf(final String text, final String characters) {
    int first = -1;
    for (final char character : characters.toCharArray()) {
      final int index = text.indexOf(character);
      if (index >= 0 && (first < 0 || index < first)) {
        first = index;
      }
    }
    return first;
  }
}
