package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the calls that set the content type of a response to JSON: {@code res.setHeader},
 * {@code header}, {@code set} or {@code append} of {@code Content-Type} with a type of {@code
 * application/json}, {@code res.type('json')} or {@code res.contentType(...)}, and {@code
 * res.writeHead(code, {'Content-Type': 'application/json'})}. The site names the response as
 * written, so a type set on another response does not count for this one.
 */
final class NodeContentTypes {
  private static final Pattern JSON_TYPE =
      Pattern.compile("^['\"`](?:json|application/(?:[\\w.+-]+\\+)?json)(?:;[^'\"`]*)?['\"`]$");
  private static final Pattern JSON_HEADER =
      Pattern.compile("^['\"`]content-type['\"`]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern JSON_FIELD =
      Pattern.compile(
          "['\"`]?content-type['\"`]?:['\"`]application/(?:[\\w.+-]+\\+)?json[;'\"`]",
          Pattern.CASE_INSENSITIVE
      );
  private static final List<String> HEADER_CALLS = List.of("setHeader", "header", "set", "append");
  private static final List<String> TYPE_CALLS = List.of("type", "contentType");
  private static final String WRITE_HEAD = "writeHead";
  private static final int HEADER_ARGUMENTS = 2;
  private static final int HEAD_BODY = 1;

  private NodeContentTypes() {}

  /** The site that a member call makes when it sets a JSON content type, if it does. */
  static Optional<FlowSite> of(
      final SyntaxNode callee,
      final List<SyntaxNode> parts,
      final List<SyntaxNode> values
  ) {
    final String name = NodeFlowSyntax.text(parts.get(parts.size() - 1));
    final List<String> arguments =
        values.stream().map(value -> NodeFlowSyntax.text(value).toLowerCase(Locale.ROOT)).toList();
    if (!setsJson(name, arguments)) {
      return Optional.empty();
    }
    return Optional.of(
        new FlowSite(
            FlowSiteKind.CONTENT_TYPE,
            "json",
            NodeFlowSyntax.text(parts.get(0)),
            Optional.empty(),
            List.of(),
            List.of(),
            callee.span()
        )
    );
  }

  private static boolean setsJson(final String name, final List<String> arguments) {
    if (HEADER_CALLS.contains(name)) {
      return arguments.size() == HEADER_ARGUMENTS
          && JSON_HEADER.matcher(arguments.get(0)).matches()
          && JSON_TYPE.matcher(arguments.get(1)).matches();
    }
    if (TYPE_CALLS.contains(name)) {
      return arguments.size() == 1 && JSON_TYPE.matcher(arguments.get(0)).matches();
    }
    return WRITE_HEAD.equals(name)
        && arguments.size() > HEAD_BODY
        && JSON_FIELD.matcher(arguments.get(HEAD_BODY)).find();
  }
}
