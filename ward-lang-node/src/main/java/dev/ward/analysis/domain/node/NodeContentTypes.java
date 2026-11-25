package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the calls that set the content type of a response, or its {@code X-Content-Type-Options}:
 * {@code res.setHeader}, {@code header}, {@code set} or {@code append} of the header, {@code
 * res.type(...)}, {@code res.contentType(...)}, {@code res.removeHeader(...)}, and {@code
 * res.writeHead(code, {...})}. Each gives sites of kind content type, named {@code json} for a JSON
 * type, {@code plain} for {@code text/plain}, {@code nosniff} for that value of the options header,
 * and {@code other} for any other value, one that is not a literal, or a removed header. The site
 * names the response as written, so a type set on another response does not count for this one.
 */
final class NodeContentTypes {
  /** The name of a JSON content type. */
  static final String JSON = "json";
  /** The name of a {@code text/plain} content type. */
  static final String PLAIN = "plain";
  /** The name of the value {@code nosniff} of {@code X-Content-Type-Options}. */
  static final String NOSNIFF = "nosniff";
  /** The name of any other content type, or of a header that is removed or not a literal. */
  static final String OTHER = "other";

  private static final Pattern JSON_TYPE =
      Pattern.compile("^['\"`](?:json|application/(?:[\\w.+-]+\\+)?json)(?:;[^'\"`]*)?['\"`]$");
  private static final Pattern PLAIN_TYPE =
      Pattern.compile("^['\"`]text/plain(?:;[^'\"`]*)?['\"`]$");
  private static final Pattern PLAIN_SHORT = Pattern.compile("^['\"`](?:text|txt)['\"`]$");
  private static final Pattern TYPE_HEADER =
      Pattern.compile("^['\"`]content-type['\"`]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern OPTIONS_HEADER =
      Pattern.compile("^['\"`]x-content-type-options['\"`]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern NOSNIFF_VALUE =
      Pattern.compile("^['\"`]nosniff['\"`]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern TYPE_FIELD =
      Pattern.compile("['\"`]?content-type['\"`]?:([^,}]*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern OPTIONS_FIELD =
      Pattern.compile(
          "['\"`]?x-content-type-options['\"`]?:['\"`]nosniff['\"`]",
          Pattern.CASE_INSENSITIVE
      );
  private static final List<String> HEADER_CALLS = List.of("setHeader", "header", "set", "append");
  private static final List<String> TYPE_CALLS = List.of("type", "contentType");
  private static final String REMOVE_HEADER = "removeHeader";
  private static final String WRITE_HEAD = "writeHead";
  private static final int HEADER_ARGUMENTS = 2;
  private static final int HEAD_BODY = 1;

  private NodeContentTypes() {}

  /** The sites that a member call makes when it sets a content type or its options. */
  static List<FlowSite> of(
      final SyntaxNode callee,
      final List<SyntaxNode> parts,
      final List<SyntaxNode> values
  ) {
    final String name = NodeFlowSyntax.text(parts.get(parts.size() - 1));
    final List<String> arguments =
        values.stream().map(value -> NodeFlowSyntax.text(value).toLowerCase(Locale.ROOT)).toList();
    final List<FlowSite> sites = new ArrayList<>();
    for (final String kind : kinds(name, arguments)) {
      sites.add(
          new FlowSite(
              FlowSiteKind.CONTENT_TYPE,
              kind,
              NodeFlowSyntax.text(parts.get(0)),
              Optional.empty(),
              List.of(),
              List.of(),
              callee.span()
          )
      );
    }
    return sites;
  }

  private static List<String> kinds(final String name, final List<String> arguments) {
    if (HEADER_CALLS.contains(name) && arguments.size() == HEADER_ARGUMENTS) {
      return header(arguments.get(0), arguments.get(1));
    }
    if (TYPE_CALLS.contains(name) && arguments.size() == 1) {
      return List.of(typeKind(arguments.get(0), true));
    }
    return removalOrHead(name, arguments);
  }

  private static List<String> removalOrHead(final String name, final List<String> arguments) {
    if (REMOVE_HEADER.equals(name) && arguments.size() == 1) {
      return TYPE_HEADER.matcher(arguments.get(0)).matches()
          || OPTIONS_HEADER.matcher(arguments.get(0)).matches() ? List.of(OTHER) : List.of();
    }
    if (WRITE_HEAD.equals(name) && arguments.size() > HEAD_BODY) {
      return head(arguments.subList(HEAD_BODY, arguments.size()));
    }
    return List.of();
  }

  private static List<String> header(final String header, final String value) {
    if (TYPE_HEADER.matcher(header).matches()) {
      return List.of(typeKind(value, false));
    }
    return OPTIONS_HEADER.matcher(header).matches() && NOSNIFF_VALUE.matcher(value).matches()
        ? List.of(NOSNIFF)
        : List.of();
  }

  private static List<String> head(final List<String> arguments) {
    final List<String> kinds = new ArrayList<>();
    for (final String argument : arguments) {
      final Matcher type = TYPE_FIELD.matcher(argument);
      if (type.find()) {
        kinds.add(typeKind(type.group(1), false));
      }
      if (OPTIONS_FIELD.matcher(argument).find()) {
        kinds.add(NOSNIFF);
      }
    }
    return kinds;
  }

  /** The kind of a content type value; {@code type()} also takes the short names. */
  private static String typeKind(final String value, final boolean shortNames) {
    if (JSON_TYPE.matcher(value).matches()) {
      return JSON;
    }
    return PLAIN_TYPE.matcher(value).matches()
        || shortNames && PLAIN_SHORT.matcher(value).matches() ? PLAIN : OTHER;
  }
}
