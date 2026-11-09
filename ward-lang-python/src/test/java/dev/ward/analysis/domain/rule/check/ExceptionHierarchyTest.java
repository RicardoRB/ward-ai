package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The builtin exception table and the ancestor lookup that the subclass-order check uses. */
final class ExceptionHierarchyTest {

  @Test
  void builtinSubclassesReachTheirBaseClassesTransitively() {
    assertThat(ExceptionHierarchy.ancestors("ValueError", Map.of()))
        .contains("Exception", "BaseException")
        .doesNotContain("LookupError");
    assertThat(ExceptionHierarchy.ancestors("KeyError", Map.of()))
        .contains("LookupError", "Exception", "BaseException");
    assertThat(ExceptionHierarchy.ancestors("FileNotFoundError", Map.of()))
        .contains("OSError", "Exception");
  }

  @Test
  void aliasesResolveToTheirBuiltinClass() {
    assertThat(ExceptionHierarchy.canonical("IOError")).isEqualTo("OSError");
    assertThat(ExceptionHierarchy.ancestors("IOError", Map.of())).contains("Exception");
  }

  @Test
  void unknownClassesHaveNoAncestors() {
    assertThat(ExceptionHierarchy.ancestors("NoSuchError", Map.of())).isEmpty();
    assertThat(ExceptionHierarchy.ancestors("BaseException", Map.of())).isEmpty();
  }

  @Test
  void moduleClassesExtendTheBuiltinTable() {
    final Map<String, List<String>> module = Map.of("MyError", List.of("ValueError"));
    assertThat(ExceptionHierarchy.ancestors("MyError", module))
        .contains("ValueError", "Exception", "BaseException");
  }

  @Test
  void moduleClassesShadowBuiltinsOfTheSameName() {
    final Map<String, List<String>> module = Map.of("ValueError", List.of());
    assertThat(ExceptionHierarchy.ancestors("ValueError", module)).isEmpty();
  }
}
