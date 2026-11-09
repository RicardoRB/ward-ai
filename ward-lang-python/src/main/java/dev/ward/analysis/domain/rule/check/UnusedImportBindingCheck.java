package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports each Python import whose bound name the file never uses, like pyflakes {@code F401}. The
 * check is file-local. A name listed in {@code __all__} and a redundant alias such as {@code import
 * x as x} are re-exports, so they are not reported; a name that a quoted annotation spells, as in
 * {@code def f(a: "Y")} or {@code x: Optional["Y"]}, is a use, and {@code __future__} imports are
 * left alone.
 */
public final class UnusedImportBindingCheck implements RuleCheck {

  private static final String FUTURE = "__future__";
  private static final String ALL = "__all__";
  private static final String EXPR_STMT = "expr_stmt";
  private static final String IMPORT_AS_NAME = "import_as_name";
  private static final String DOTTED_AS_NAME = "dotted_as_name";
  private static final String AS = "as";
  private static final int ALIAS_PARTS = 3;
  private static final String ASSIGN = "=";
  private static final String ARROW = "->";
  private static final String TFPDEF = "tfpdef";
  private static final String ANNASSIGN = "annassign";
  private static final String FUNCDEF = "funcdef";
  private static final Pattern WORD = Pattern.compile("[A-Za-z_]\\w*");
  private static final String NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the bound name
   */
  public UnusedImportBindingCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unused-import check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(Set.of());
    final Set<String> reexported = reexported(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      for (final Binding binding : scope.bindings()) {
        if (unused(binding, scopes, reexported)) {
          found.add(new RuleMatch(binding.declaration(), message.replace(NAME, binding.name())));
        }
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private static boolean unused(
      final Binding binding, final UnitScopes scopes, final Set<String> reexported) {
    return binding.kind() == BindingKind.IMPORT
        && !isFuture(binding)
        && !reexported.contains(binding.name())
        && scopes.usesOf(binding).stream().noneMatch(use -> use.access() != ReferenceAccess.WRITE);
  }

  private static boolean isFuture(final Binding binding) {
    return binding.origin().map(ImportOrigin::specifier).filter(FUTURE::equals).isPresent();
  }

  /**
   * The names that the file spells outside the code's own uses: those of {@code __all__}, the
   * redundant aliases, and the words of quoted annotations.
   */
  private static Set<String> reexported(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (EXPR_STMT.equals(node.kind()) && SyntaxWalk.text(node).startsWith(ALL)) {
        addWordsInStrings(node, names);
      }
      if (isRedundantAlias(node)) {
        names.add(SyntaxWalk.text(node.children().get(2)));
      }
      addAnnotationWords(node, names);
    }
    return names;
  }

  /**
   * Adds the words of the quoted parts of an annotation: a parameter's or a variable's annotation
   * up to its value, and a function's return annotation after {@code ->}.
   */
  private static void addAnnotationWords(final SyntaxNode node, final Set<String> words) {
    if (TFPDEF.equals(node.kind())) {
      addWordsInStrings(node, words);
    } else if (ANNASSIGN.equals(node.kind())) {
      addVariableWords(node, words);
    } else if (FUNCDEF.equals(node.kind())) {
      addReturnWords(node, words);
    }
  }

  /** The words of a variable annotation: its parts before the value. */
  private static void addVariableWords(final SyntaxNode annotation, final Set<String> words) {
    for (final SyntaxNode part : annotation.children()) {
      if (ASSIGN.equals(SyntaxWalk.text(part))) {
        return;
      }
      addWordsInStrings(part, words);
    }
  }

  /** The words of a function's return annotation, the part after {@code ->}. */
  private static void addReturnWords(final SyntaxNode function, final Set<String> words) {
    final List<SyntaxNode> parts = function.children();
    for (int index = 0; index < parts.size() - 1; index++) {
      if (ARROW.equals(SyntaxWalk.text(parts.get(index)))) {
        addWordsInStrings(parts.get(index + 1), words);
      }
    }
  }

  /** Whether {@code node} is {@code x as x}, a module or member imported under its own name. */
  private static boolean isRedundantAlias(final SyntaxNode node) {
    if (!IMPORT_AS_NAME.equals(node.kind()) && !DOTTED_AS_NAME.equals(node.kind())) {
      return false;
    }
    final List<SyntaxNode> parts = node.children();
    return parts.size() == ALIAS_PARTS
        && AS.equals(SyntaxWalk.text(parts.get(1)))
        && SyntaxWalk.text(parts.get(0)).equals(SyntaxWalk.text(parts.get(2)));
  }

  private static void addWordsInStrings(final SyntaxNode node, final Set<String> words) {
    for (final SyntaxNode leaf : SyntaxWalk.leaves(node)) {
      final String text = SyntaxWalk.text(leaf);
      if (text.indexOf('\'') >= 0 || text.indexOf('"') >= 0) {
        final Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
          words.add(matcher.group());
        }
      }
    }
  }
}
