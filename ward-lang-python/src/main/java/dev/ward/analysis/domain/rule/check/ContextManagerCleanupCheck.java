package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a generator that uses a same-module {@code @contextmanager} function whose yield of a
 * non-constant value does not clean up when the generator is closed, like Pylint's {@code
 * contextmanager-generator-missing-cleanup}. A context manager whose last statement is the yield,
 * or whose yield sits in a {@code try} that handles {@code GeneratorExit}, {@code Exception} or
 * everything, is not reported. Shortcut: only the functions of the same file are read.
 */
public final class ContextManagerCleanupCheck implements RuleCheck {
  private static final String WITH_ITEM = "with_item";

  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> SCOPES =
      Set.of("funcdef", "async_funcdef", "lambdef", "classdef");
  private static final Set<String> HANDLED = Set.of("GeneratorExit", "Exception", "BaseException");
  private static final Pattern CONSTANT =
      Pattern.compile("^(None|True|False|\\d[\\w.]*|'[^']*'|\"[^\"]*\")$");
  private static final Pattern CALL_OF = Pattern.compile("^(\\w+)\\(.*");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ContextManagerCleanupCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> failing = failingContextManagers(root);
    final List<RuleMatch> found = new ArrayList<>();
    if (failing.isEmpty()) {
      return found;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      if (!ownYields(visit.node()).isEmpty()) {
        for (final SyntaxWalk.Visit with : SyntaxWalk.visits(visit.node(), Set.of("with_stmt"))) {
          if (usesFailing(with.node(), failing)) {
            found.add(new RuleMatch(with.node().span(), message));
          }
        }
      }
    }
    return List.copyOf(found);
  }

  /** The names of the same-file context managers whose yield does not clean up. */
  private static Set<String> failingContextManagers(final SyntaxNode root) {
    final Set<String> failing = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      if (isContextManager(visit.ancestors()) && cleanupMissing(visit.node())) {
        failing.add(nameOf(visit.node()));
      }
    }
    return failing;
  }

  private static boolean isContextManager(final List<SyntaxNode> ancestors) {
    if (ancestors.isEmpty()) {
      return false;
    }
    final SyntaxNode parent = ancestors.get(ancestors.size() - 1);
    final String text = SyntaxWalk.text(parent);
    return "decorated".equals(parent.kind())
        && (text.contains("@contextmanager") || text.contains("@contextlib.contextmanager"));
  }

  /** Whether a context manager yields a non-constant value without a handler for its exit. */
  private static boolean cleanupMissing(final SyntaxNode function) {
    final List<SyntaxNode> yields = ownYields(function);
    if (yields.isEmpty()) {
      return false;
    }
    for (final SyntaxNode yield : yields) {
      final String value = SyntaxWalk.text(yield).replaceFirst("^yield(from)?", "");
      if (value.isEmpty() || CONSTANT.matcher(value).matches()) {
        return false;
      }
    }
    if (lastStatementYields(function)) {
      return false;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(function, Set.of("try_stmt"))) {
      if (handlesExit(visit.node())) {
        return false;
      }
    }
    return true;
  }

  /** The yields of the function's own body, not those of a nested function or class. */
  private static List<SyntaxNode> ownYields(final SyntaxNode function) {
    final List<SyntaxNode> yields = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(function, Set.of("yield_expr"))) {
      if (visit.ancestors().stream().skip(1).noneMatch(a -> SCOPES.contains(a.kind()))) {
        yields.add(visit.node());
      }
    }
    return yields;
  }

  /** Whether the last statement of the function's block is the yield. */
  private static boolean lastStatementYields(final SyntaxNode function) {
    final SyntaxNode block =
        function.children().stream()
            .filter(child -> "block".equals(child.kind()))
            .findFirst()
            .orElse(null);
    if (block == null) {
      return false;
    }
    final List<SyntaxNode> statements =
        block.children().stream().filter(child -> "stmt".equals(child.kind())).toList();
    return !statements.isEmpty()
        && SyntaxWalk.text(statements.get(statements.size() - 1)).contains("yield");
  }

  /** Whether a try statement with a yield in it handles the exit of the generator. */
  private static boolean handlesExit(final SyntaxNode tryStatement) {
    if (SyntaxWalk.visits(tryStatement, Set.of("yield_expr")).isEmpty()) {
      return false;
    }
    for (final SyntaxNode clause : tryStatement.children()) {
      if (!"except_clause".equals(clause.kind())) {
        continue;
      }
      final String text = SyntaxWalk.text(clause);
      if ("except:".equals(text)
          || "except".equals(text)
          || HANDLED.stream().anyMatch(name -> text.startsWith("except" + name))) {
        return true;
      }
    }
    return false;
  }

  /** Whether a with statement enters one of the failing context managers. */
  private static boolean usesFailing(final SyntaxNode with, final Set<String> failing) {
    for (final SyntaxNode item : with.children()) {
      if (WITH_ITEM.equals(item.kind())) {
        final Matcher matcher = CALL_OF.matcher(SyntaxWalk.text(item));
        if (matcher.matches() && failing.contains(matcher.group(1))) {
          return true;
        }
      }
    }
    return false;
  }

  private static String nameOf(final SyntaxNode function) {
    return function.children().stream()
        .filter(child -> "name".equals(child.kind()) || "NAME".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
