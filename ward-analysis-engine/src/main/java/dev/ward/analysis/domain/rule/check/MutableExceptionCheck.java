package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reports each instance field that is not final in an exception class. A class is an exception when
 * its name matches the format, or when the class it extends matches the extended format.
 */
public final class MutableExceptionCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final String FIELD = "fieldDeclaration";

  private final Pattern format;
  private final Pattern extendedClassNameFormat;
  private final String message;

  /**
   * Creates the check.
   *
   * @param format the pattern an exception class name matches
   * @param extendedClassNameFormat the pattern the name of an exception's superclass matches
   * @param message the message; {@code {value}} is the field name
   */
  public MutableExceptionCheck(
      final Pattern format, final Pattern extendedClassNameFormat, final String message) {
    this.format = Objects.requireNonNull(format, "format");
    this.extendedClassNameFormat =
        Objects.requireNonNull(extendedClassNameFormat, "extendedClassNameFormat");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      if (CLASS.equals(type.kind()) && isException(type)) {
        DesignMembers.body(type).ifPresent(body -> mutableFields(body, matches));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isException(final SyntaxNode type) {
    return format.matcher(JUnitMembers.name(type)).find()
        || DeclarationText.superclassName(type)
            .map(name -> extendedClassNameFormat.matcher(name).find())
            .orElse(false);
  }

  private void mutableFields(final SyntaxNode body, final List<RuleMatch> matches) {
    for (final DesignMembers.Member member : DesignMembers.members(body)) {
      final SyntaxNode field = member.declaration();
      if (FIELD.equals(field.kind()) && !member.has("static") && !member.has("final")) {
        for (final String name : DeclarationText.declaredNames(field)) {
          matches.add(CheckSupport.at(field.span(), CheckSupport.fill(message, name, "")));
        }
      }
    }
  }
}
