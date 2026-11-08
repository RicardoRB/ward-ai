package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports classes of one file whose static initializers use each other or their own subclass: two
 * classes that each create or call into the other while they initialize, and a class that creates a
 * subclass of itself while it initializes. Only classes declared in the same file are read, so a
 * class that is not in the file is never reported.
 */
public final class StaticInitializationCheck implements RuleCheck {

  /** What the check looks for. */
  public enum Mode {
    /** Two classes whose static initializers use each other. */
    CIRCULAR,
    /** A class whose static initializer uses one of its subclasses. */
    SUPERCLASS_USES_SUBCLASS
  }

  private static final String CLASS = "classDeclaration";
  private static final String BODY = "classBody";
  private static final String DECLARATION = "classBodyDeclaration";
  private static final String BLOCK = "block";
  private static final String STATIC_TOKEN = "'static'";
  private static final String EXTENDS = "'extends'";
  private static final String TYPE_TYPE = "typeType";

  private final Mode mode;
  private final String message;

  /** A class of the file with the text of its static initializers. */
  private record Type(SyntaxNode node, String name, String superclass, String initializers) {

    boolean uses(final Type other) {
      return Pattern.compile(
              "(?<![\\w.])new" + other.name() + "[(<]|(?<![\\w.])" + other.name() + "\\.")
          .matcher(initializers)
          .find();
    }
  }

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at the class
   */
  public StaticInitializationCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<Type> types =
        SyntaxWalk.visits(root, Set.of(CLASS)).stream()
            .map(SyntaxWalk.Visit::node)
            .map(StaticInitializationCheck::type)
            .toList();
    final List<RuleMatch> matches = new ArrayList<>();
    for (int index = 0; index < types.size(); index++) {
      final Type type = types.get(index);
      if (reported(type, types.subList(index + 1, types.size()), types)) {
        matches.add(CheckSupport.at(type.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean reported(final Type type, final List<Type> later, final List<Type> all) {
    return mode == Mode.CIRCULAR
        ? later.stream().anyMatch(other -> type.uses(other) && other.uses(type))
        : all.stream()
            .anyMatch(other -> type.name().equals(other.superclass()) && type.uses(other));
  }

  private static Type type(final SyntaxNode clazz) {
    final StringBuilder initializers = new StringBuilder();
    for (final SyntaxNode body : JavaNodes.kids(clazz, BODY)) {
      for (final SyntaxNode member : JavaNodes.kids(body, DECLARATION)) {
        if (isStaticBlock(member) || isStaticField(member)) {
          initializers.append(SyntaxWalk.text(member));
        }
      }
    }
    return new Type(clazz, ClassMembers.nameOf(clazz), superclass(clazz), initializers.toString());
  }

  private static boolean isStaticBlock(final SyntaxNode member) {
    return member.children().stream().anyMatch(child -> STATIC_TOKEN.equals(child.kind()))
        && member.children().stream().anyMatch(child -> BLOCK.equals(child.kind()));
  }

  private static boolean isStaticField(final SyntaxNode member) {
    return MemberDeclarations.declaration(member)
            .filter(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
            .isPresent()
        && MemberDeclarations.modifiers(member).contains(MemberDeclarations.STATIC);
  }

  private static String superclass(final SyntaxNode clazz) {
    final List<SyntaxNode> parts = clazz.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (EXTENDS.equals(parts.get(index).kind())
          && TYPE_TYPE.equals(parts.get(index + 1).kind())) {
        return DeclaredMethods.simpleName(SyntaxWalk.text(parts.get(index + 1)));
      }
    }
    return "";
  }
}
