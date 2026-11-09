package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.ClassCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.Filters;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.MemberCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.NameCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.NodeCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.ScopeCondition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DecoratedClassCheckTest {

  private static final String COMPONENT =
      """
      @Component({ selector: 'a' })
      export class A {
        @Input('title') title = '';
        ngOnInit(): void {}
        ngDoBootstrap(): void {}
      }
      """;

  private static final ClassCondition NO_CLASS =
      new ClassCondition(Optional.empty(), Map.of(), Optional.empty());
  private static final MemberCondition NO_MEMBER =
      new MemberCondition(Optional.empty(), Set.of(), Optional.empty());
  private static final NodeCondition NO_NODE =
      new NodeCondition(Optional.empty(), Optional.empty(), false, Optional.empty());
  private static final ScopeCondition NO_SCOPE = new ScopeCondition(Set.of(), Set.of(), Set.of());
  private static final NameCondition NO_NAME =
      new NameCondition(Map.of(), Optional.empty(), Set.of(), false);

  @Test
  void reportsOnlyNodesOfAMatchingDecoratedClass() {
    final Filters filters =
        new Filters(
            new ClassCondition(
                Optional.of(Pattern.compile("^@Component\\b")), Map.of(), Optional.empty()),
            NO_MEMBER,
            NO_NODE,
            new ScopeCondition(Set.of(), Set.of("propertyMemberDeclaration"), Set.of()),
            NO_NAME);

    assertThat(lines(filters, Set.of("propertyName"), COMPONENT)).containsExactly(3, 4, 5);
    assertThat(lines(filters, Set.of("propertyName"), "class A { ngOnInit(): void {} }\n"))
        .isEmpty();
  }

  @Test
  void keepsAMemberOnlyWhenItsDecoratorMatches() {
    final Filters filters =
        new Filters(
            NO_CLASS,
            new MemberCondition(
                Optional.of(Pattern.compile("^@Input\\b")), Set.of(), Optional.empty()),
            NO_NODE,
            NO_SCOPE,
            NO_NAME);

    assertThat(lines(filters, Set.of("propertyMemberDeclaration"), COMPONENT)).containsExactly(3);
  }

  @Test
  void pairsPickTheTextAllowedForEachClassDecorator() {
    final Filters filters =
        new Filters(
            new ClassCondition(
                Optional.empty(),
                Map.of(Pattern.compile("^@Component\\b"), Pattern.compile("^ngDoBootstrap$")),
                Optional.empty()),
            NO_MEMBER,
            NO_NODE,
            NO_SCOPE,
            NO_NAME);

    assertThat(lines(filters, Set.of("propertyName"), COMPONENT)).containsExactly(5);
  }

  @Test
  void aliasInsideTheAllowedNamesOrTheAriaNameIsNotReported() {
    final Pattern alias = Pattern.compile("^@Input\\((?:['\"])([^'\"]*)");
    final Filters filters =
        new Filters(
            NO_CLASS,
            NO_MEMBER,
            NO_NODE,
            NO_SCOPE,
            new NameCondition(Map.of(), Optional.of(alias), Set.of("title"), true));
    final String source =
        """
        class A {
          @Input('title') title = '';
          @Input('size') size = 0;
          @Input('aria-label') ariaLabel = '';
        }
        """;

    assertThat(lines(filters, Set.of("decorator"), source)).containsExactly(3);
  }

  @Test
  void heritageMissingReportsOnlyTheMethodsOfClassesThatDoNotImplementTheirInterface() {
    final Filters filters =
        new Filters(
            NO_CLASS,
            NO_MEMBER,
            NO_NODE,
            NO_SCOPE,
            new NameCondition(Map.of("ngOnInit", "OnInit"), Optional.empty(), Set.of(), false));
    final String source =
        """
        class A implements OnInit {
          ngOnInit(): void {}
        }
        class B {
          ngOnInit(): void {}
        }
        """;

    assertThat(lines(filters, Set.of("propertyName"), source)).containsExactly(5);
  }

  @Test
  void inClassDecoratorLimitsTheCheckToTheClassDecoratorArguments() {
    final Filters filters =
        new Filters(
            NO_CLASS,
            NO_MEMBER,
            new NodeCondition(
                Optional.of(Pattern.compile("^inputs:")),
                Optional.empty(),
                true,
                Optional.of(Pattern.compile("^hostDirectives:"))),
            NO_SCOPE,
            NO_NAME);
    final String source =
        """
        @Component({
          selector: 'a',
          inputs: ['x'],
          hostDirectives: [{ directive: B, inputs: ['y'] }],
        })
        class A {
          method() { const o = { inputs: 1 }; }
        }
        """;

    assertThat(lines(filters, Set.of("propertyAssignment"), source)).containsExactly(3);
  }

  @Test
  void parentFilterThePlace() {
    final Filters filters =
        new Filters(
            NO_CLASS,
            NO_MEMBER,
            NO_NODE,
            new ScopeCondition(Set.of(), Set.of("propertyMemberDeclaration"), Set.of()),
            NO_NAME);

    assertThat(lines(filters, Set.of("propertyName"), COMPONENT)).containsExactly(3, 4, 5);
  }

  @Test
  void aClassNameIsReportedWhenItsPipeDoesNotImplementTheInterface() {
    final Filters filters =
        new Filters(
            new ClassCondition(
                Optional.of(Pattern.compile("^@Pipe\\b")), Map.of(), Optional.of("PipeTransform")),
            NO_MEMBER,
            NO_NODE,
            new ScopeCondition(Set.of(), Set.of("classDeclaration"), Set.of()),
            NO_NAME);
    final String source =
        """
        @Pipe({ name: 'shout' })
        export class ShoutPipe {}
        """;

    assertThat(lines(filters, Set.of("identifier"), source)).containsExactly(2);
  }

  private static List<Integer> lines(
      final Filters filters, final Set<String> kinds, final String source) {
    final SyntaxNode root = parse(source);
    return new DecoratedClassCheck(kinds, filters, "message")
        .matches(root).stream().map(RuleMatch::span).map(span -> span.startLine()).toList();
  }

  private static SyntaxNode parse(final String source) {
    final SourceUnit unit = new SourceUnit("src/a.ts", AnalysisLanguage.TYPESCRIPT, source);
    final ParseResult parsed = new AntlrLanguageFrontend().parse(unit);
    return parsed.tree();
  }
}
