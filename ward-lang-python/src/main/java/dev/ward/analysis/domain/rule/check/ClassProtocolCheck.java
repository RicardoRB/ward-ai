package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a class of this module used in a way its body does not allow, like pylint {@code
 * not-context-manager}, {@code not-async-context-manager}, {@code non-iterator-returned}, {@code
 * invalid-enum-extension} and {@code invalid-metaclass}. A base or metaclass that is not a class of
 * this module is unknown, and nothing is reported for it.
 */
public final class ClassProtocolCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** A {@code with} on a class that has no {@code __enter__} and {@code __exit__}. */
    NOT_CONTEXT_MANAGER,
    /** An {@code async with} on a class that has no {@code __aenter__} and {@code __aexit__}. */
    NOT_ASYNC_CONTEXT_MANAGER,
    /** An {@code __iter__} that returns {@code self} from a class with no {@code __next__}. */
    NON_ITERATOR_RETURNED,
    /** A class that derives from an enum of this module that has members. */
    INVALID_ENUM_EXTENSION,
    /** A {@code metaclass=} that is not a class deriving from {@code type}. */
    INVALID_METACLASS,
    /** A class that defines {@code __eq__} but not {@code __hash__}. */
    EQ_WITHOUT_HASH
  }

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> WITHS = Set.of("with_stmt", "async_stmt");
  private static final Pattern WITH_CALL = Pattern.compile("^with\\s*(\\w+)\\(");
  private static final Pattern ASYNC_WITH_CALL = Pattern.compile("^async\\s*with\\s*(\\w+)\\(");
  private static final Pattern METACLASS = Pattern.compile("metaclass=(\\w+)");

  private static final Protocol WITH = new Protocol(WITH_CALL, "__enter__", "__exit__");
  private static final Protocol ASYNC = new Protocol(ASYNC_WITH_CALL, "__aenter__", "__aexit__");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public ClassProtocolCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, ClassIndex.Info> classes = ClassIndex.classes(root);
    return switch (problem) {
      case NOT_CONTEXT_MANAGER -> withCalls(root, WITH, classes);
      case NOT_ASYNC_CONTEXT_MANAGER -> withCalls(root, ASYNC, classes);
      case NON_ITERATOR_RETURNED -> nonIterators(root, classes);
      case INVALID_ENUM_EXTENSION -> enumExtensions(root, classes);
      case INVALID_METACLASS -> invalidMetaclasses(root, classes);
      case EQ_WITHOUT_HASH -> eqWithoutHash(classes);
    };
  }

  /** The with statement to look for and the two methods a context manager needs. */
  private record Protocol(Pattern call, String enter, String exit) {}

  private List<RuleMatch> withCalls(
      final SyntaxNode root, final Protocol protocol, final Map<String, ClassIndex.Info> classes) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, WITHS)) {
      final Matcher named = protocol.call().matcher(SyntaxWalk.text(visit.node()));
      final ClassIndex.Info info = named.find() ? classes.get(named.group(1)) : null;
      if (info != null
          && info.bases().isEmpty()
          && !(info.members().contains(protocol.enter())
              && info.members().contains(protocol.exit()))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> nonIterators(
      final SyntaxNode root, final Map<String, ClassIndex.Info> classes) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String text = SyntaxWalk.text(visit.node());
      final ClassIndex.Info info = classes.get(ClassIndex.enclosingClass(visit.ancestors()));
      if (text.startsWith("def__iter__")
          && info != null
          && !info.members().contains("__next__")
          && returnsSelf(text)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean returnsSelf(final String functionText) {
    return functionText.contains("returnself") && !functionText.contains("returnself.");
  }

  private List<RuleMatch> enumExtensions(
      final SyntaxNode root, final Map<String, ClassIndex.Info> classes) {
    final List<RuleMatch> found = new ArrayList<>();
    final Set<String> assigners = ClassIndex.classesWithAssignments(root, classes.keySet());
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final ClassIndex.Info info =
          classes.get(ClassIndex.classNameOf(SyntaxWalk.text(visit.node())));
      if (info != null
          && info.bases().stream().anyMatch(base -> enumWithMembers(base, classes, assigners))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean enumWithMembers(
      final String base, final Map<String, ClassIndex.Info> classes, final Set<String> assigners) {
    final ClassIndex.Info parent = classes.get(base);
    return parent != null
        && parent.bases().stream().anyMatch(name -> name.endsWith("Enum"))
        && assigners.contains(base);
  }

  private List<RuleMatch> invalidMetaclasses(
      final SyntaxNode root, final Map<String, ClassIndex.Info> classes) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final Matcher meta = METACLASS.matcher(SyntaxWalk.text(visit.node()));
      if (meta.find() && invalidMetaclass(meta.group(1), classes)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> eqWithoutHash(final Map<String, ClassIndex.Info> classes) {
    return classes.values().stream()
        .filter(info -> info.members().contains("__eq__") && !info.text().contains("__hash__"))
        .map(info -> new RuleMatch(info.node().span(), message))
        .toList();
  }

  private static boolean invalidMetaclass(
      final String name, final Map<String, ClassIndex.Info> classes) {
    if (name.matches("\\d.*|None|True|False")) {
      return true;
    }
    final ClassIndex.Info info = classes.get(name);
    return info != null && info.bases().isEmpty();
  }
}
