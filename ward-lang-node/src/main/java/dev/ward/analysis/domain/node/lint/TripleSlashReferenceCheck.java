package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a triple-slash reference in the header of a file, as typescript-eslint does: a {@code
 * lib} or {@code path} reference by its option, and a {@code types} reference only when the file
 * also imports that module under {@code prefer-import}.
 */
public final class TripleSlashReferenceCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final String IMPORT = "importStatement";
  private static final String MODULE = "{module}";
  private static final String NEVER = "never";
  private static final String PREFER_IMPORT = "prefer-import";
  private static final Pattern REFERENCE =
      Pattern.compile("^///\\s*<reference\\s+(types|path|lib)\\s*=\\s*[\"']([^\"']*)[\"']");
  private static final Pattern QUOTED = Pattern.compile("^[\"'](.*)[\"']$");

  private final String lib;
  private final String path;
  private final String types;
  private final String message;

  /** Creates the check with the value of each reference kind's option and the message. */
  public TripleSlashReferenceCheck(
      final String lib, final String path, final String types, final String message) {
    this.lib = lib;
    this.path = path;
    this.types = types;
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final int firstCode = firstCodeLine(root);
    final Set<String> imported = importedModules(root);
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode comment : SyntaxText.nodesOf(root, COMMENT)) {
      final Matcher reference = REFERENCE.matcher(SyntaxText.of(comment));
      if (comment.span().startLine() < firstCode
          && reference.find()
          && isReported(reference.group(1), reference.group(2), imported)) {
        out.add(new RuleMatch(comment.span(), message.replace(MODULE, reference.group(2))));
      }
    }
    return out;
  }

  private boolean isReported(final String kind, final String module, final Set<String> imported) {
    final String setting =
        switch (kind) {
          case "lib" -> lib;
          case "path" -> path;
          default -> types;
        };
    return NEVER.equals(setting) || PREFER_IMPORT.equals(setting) && imported.contains(module);
  }

  /** Returns the modules the file imports, as the quoted source of each import statement. */
  private static Set<String> importedModules(final SyntaxNode root) {
    final Set<String> modules = new HashSet<>();
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, IMPORT)) {
      for (final SyntaxNode leaf : SyntaxText.leaves(statement)) {
        final Matcher quoted = QUOTED.matcher(SyntaxText.of(leaf));
        if (quoted.matches()) {
          modules.add(quoted.group(1));
          break;
        }
      }
    }
    return modules;
  }

  /** Returns the line of the first token that is not a comment, or the last line when none. */
  private static int firstCodeLine(final SyntaxNode root) {
    return SyntaxText.leaves(root).stream()
        .filter(leaf -> !COMMENT.equals(leaf.kind()))
        .findFirst()
        .map(leaf -> leaf.span().startLine())
        .orElse(Integer.MAX_VALUE);
  }
}
