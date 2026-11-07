package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidJavaBeanCheckTest extends ClassMemberTrees {

  private static final String MESSAGE = "{value}";
  private static final String CUSTOMER = "Customer";
  private static final String NAME = "name";
  private static final String AGE = "age";
  private static final String ACTIVE = "active";
  private static final String BOOLEAN = "boolean";
  private static final String GET_AGE = "getAge";
  private static final String SET_AGE = "setAge";
  private static final String IS_ACTIVE = "isActive";
  private static final String SET_ACTIVE = "setActive";
  private static final String OTHER_PACKAGE = "com.other";

  @Test
  void reportsBeanFieldsWithoutAGetterAndASetterOfTheirType() {
    final SyntaxNode root =
        packageUnit(
            PACKAGE_NAME,
            classNamed(
                CUSTOMER, member(field(NAME), PRIVATE), member(method("getName", 0, 0), PUBLIC)));

    assertThat(bean(root, List.of(PACKAGE_NAME))).containsExactly(NAME);
  }

  @Test
  void acceptsBeanFieldsWithAGetterAndASetterOfTheirType() {
    final SyntaxNode root =
        packageUnit(
            PACKAGE_NAME,
            classNamed(
                CUSTOMER,
                member(typedField(AGE, INT), PRIVATE),
                member(typedMethod(GET_AGE, INT), PUBLIC),
                member(typedSetter(SET_AGE, INT), PUBLIC),
                member(typedField(ACTIVE, BOOLEAN), PRIVATE),
                member(typedMethod(IS_ACTIVE, BOOLEAN), PUBLIC),
                member(typedSetter(SET_ACTIVE, BOOLEAN), PUBLIC),
                member(typedField("cache", INT), PRIVATE, STATIC),
                member(typedField("scratch", INT), PRIVATE, TRANSIENT)));

    assertThat(bean(root, List.of(PACKAGE_NAME))).isEmpty();
  }

  @Test
  void reportsBeanClassesWithoutANoArgConstructor() {
    final SyntaxNode withArgument =
        packageUnit(PACKAGE_NAME, classNamed(CUSTOMER, member(constructorWith(1), PUBLIC)));

    assertThat(bean(withArgument, List.of(PACKAGE_NAME))).containsExactly("Customer constructor");
  }

  @Test
  void skipsUntypedFieldsAndBeansOutsideTheConfiguredPackages() {
    final SyntaxNode untyped =
        packageUnit(
            PACKAGE_NAME,
            classNamed(
                "Untyped",
                member(
                    node(
                        "fieldDeclaration",
                        LINE,
                        COLUMN,
                        node(
                            DECLARATOR,
                            LINE,
                            COLUMN,
                            node(VARIABLE_DECLARATOR_ID, LINE, COLUMN, ident("loose")))),
                    PRIVATE)));
    final SyntaxNode otherPackage =
        packageUnit(OTHER_PACKAGE, classNamed(CUSTOMER, member(field(NAME), PRIVATE)));

    assertThat(bean(untyped, List.of(PACKAGE_NAME))).isEmpty();
    assertThat(bean(otherPackage, List.of(PACKAGE_NAME))).isEmpty();
    assertThat(bean(otherPackage, List.of())).isEmpty();
  }

  private static List<String> bean(final SyntaxNode root, final List<String> packages) {
    return Trees.messages(
        new InvalidJavaBeanCheck(packages, MESSAGE).check(root, SourceFile.UNKNOWN));
  }
}
