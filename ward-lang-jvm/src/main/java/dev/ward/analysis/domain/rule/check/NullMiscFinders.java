package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The rules about where a nullness annotation sits and about {@code Optional.get}. */
final class NullMiscFinders {

  private static final Pattern ABSENT =
      Pattern.compile("^(?:!(\\w+)\\.isPresent\\(\\)|(\\w+)\\.isEmpty\\(\\))$");
  private static final String NULLABLE = "Nullable";

  /** The index of the then-statement among the children of an if statement. */
  private static final int THEN = 4;

  private static final String ANNOTATION = "annotation";
  private static final Set<String> TYPE_USE_IMPORTS =
      Set.of(
          "org.jspecify.annotations.Nullable",
          "org.checkerframework.checker.nullness.qual.Nullable");

  private static final Set<String> ENTRIES =
      Set.of(
          "classBodyDeclaration",
          "interfaceBodyDeclaration",
          "formalParameter",
          "localVariableDeclaration");
  private static final Set<String> DECLARING =
      Set.of(
          "typeTypeOrVoid",
          "fieldDeclaration",
          "methodDeclaration",
          "memberDeclaration",
          "interfaceMemberDeclaration",
          "interfaceMethodDeclaration",
          "interfaceCommonBodyDeclaration",
          "genericMethodDeclaration",
          "classBodyDeclaration",
          "interfaceBodyDeclaration",
          "formalParameter",
          "localVariableDeclaration");

  private NullMiscFinders() {}

  /**
   * A type-use {@code @Nullable} that is written before a qualified name, so it marks the outer
   * class.
   */
  static List<NullHit> nullableOnWrongNestedClassLevel(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    if (!importsTypeUse(unit.root())) {
      return hits;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(unit.root(), Set.of("typeType"))) {
      final SyntaxNode type = visit.node();
      final List<SyntaxNode> leading = leading(visit);
      final boolean nullable =
          leading.stream().anyMatch(note -> NULLABLE.equals(NullSyntax.simpleName(note)));
      if (nullable && qualified(type)) {
        hits.add(NullHit.at(leading.get(0), SyntaxWalk.text(type)));
      }
    }
    return hits;
  }

  /**
   * The annotations before a type: its own, and those of the field, parameter, local or method
   * whose type it is. A type nested in type arguments has only its own.
   */
  private static List<SyntaxNode> leading(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> notes = new ArrayList<>(NullSyntax.annotations(visit.node()));
    for (int index = visit.ancestors().size() - 1; index >= 0; index--) {
      final SyntaxNode ancestor = visit.ancestors().get(index);
      if (!DECLARING.contains(ancestor.kind())) {
        break;
      }
      notes.addAll(NullSyntax.annotations(ancestor));
      if (ENTRIES.contains(ancestor.kind())) {
        break;
      }
    }
    return notes;
  }

  private static boolean importsTypeUse(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of("importDeclaration")).stream()
        .map(visit -> SyntaxWalk.text(visit.node()))
        .anyMatch(text -> TYPE_USE_IMPORTS.stream().anyMatch(text::contains));
  }

  private static boolean qualified(final SyntaxNode type) {
    return NullSyntax.first(type, "classOrInterfaceType")
        .map(
            named -> {
              final String text = SyntaxWalk.text(named);
              final int generic = text.indexOf('<');
              final String head = generic < 0 ? text : text.substring(0, generic);
              return head.indexOf('.') > 0
                  && Character.isUpperCase(head.charAt(0))
                  && SyntaxWalk.visits(named, Set.of(ANNOTATION)).isEmpty();
            })
        .orElse(false);
  }

  /** A {@code @Nullable} written between the type of a variable-arity parameter and its dots. */
  static List<NullHit> nullableVarargsUnsupported(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(unit.root(), Set.of("formalParameter"))) {
      visit.node().children().stream()
          .filter(child -> ANNOTATION.equals(child.kind()))
          .filter(child -> NULLABLE.equals(NullSyntax.simpleName(child)))
          .forEach(child -> hits.add(NullHit.at(child, SyntaxWalk.text(visit.node()))));
    }
    return hits;
  }

  /** A {@code get} on an optional that is known to be empty. */
  static List<NullHit> getOnEmptyOptional(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(unit.root(), Set.of("expression"))) {
      NullFlow.call(visit.node())
          .filter(call -> "get".equals(call.name()) && call.args().isEmpty())
          .filter(call -> "Optional.empty()".equals(call.receiver()))
          .ifPresent(call -> hits.add(NullHit.at(visit.node(), call.receiver())));
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(unit.root(), Set.of("statement"))) {
      guarded(visit.node(), hits);
    }
    return hits;
  }

  /**
   * The {@code get} calls in the then-branch of {@code if (!x.isPresent())} or {@code x.isEmpty()}.
   */
  private static void guarded(final SyntaxNode statement, final List<NullHit> hits) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.size() <= THEN || !"if".equals(SyntaxWalk.text(parts.get(0)))) {
      return;
    }
    final Matcher absent = ABSENT.matcher(SyntaxWalk.text(parts.get(2)));
    if (!absent.matches()) {
      return;
    }
    final String name = absent.group(1) != null ? absent.group(1) : absent.group(2);
    for (final SyntaxNode expression : NullSyntax.expressions(parts.get(THEN))) {
      NullFlow.call(expression)
          .filter(call -> "get".equals(call.name()) && name.equals(call.receiver()))
          .ifPresent(call -> hits.add(NullHit.at(expression, name)));
    }
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept(
        "nullable-on-wrong-nested-class-level", NullMiscFinders::nullableOnWrongNestedClassLevel);
    action.accept("nullable-varargs-unsupported", NullMiscFinders::nullableVarargsUnsupported);
    action.accept("get-on-empty-optional", NullMiscFinders::getOnEmptyOptional);
  }
}
