package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JvmClassesMembersTest {
  private static final int READS_WITHOUT_SHARING = 3;

  private static JvmClass classNamed(final String name) {
    return new JvmClass(
        name,
        JvmClass.Kind.CLASS,
        0,
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        false,
        false,
        Map.of());
  }

  private static TypeMembers empty() {
    return new TypeMembers(List.of(), List.of(), List.of(), List.of());
  }

  private static JvmClasses classes() {
    final JvmLibrary library = name -> Optional.of(classNamed(name));
    final JvmSources sources = name -> Optional.empty();
    return new JvmClasses(library, sources, new AntlrLanguageFrontend());
  }

  @Test
  void twoUnitsGetTheSameMembersOfALibraryClassReadOnce() {
    final JvmClasses project = classes();
    final JvmClasses first = project.withLocal(Map.of("own.A", classNamed("own.A")));
    final JvmClasses second = project.withLocal(Map.of("own.B", classNamed("own.B")));
    final AtomicInteger reads = new AtomicInteger();

    final TypeMembers one =
        first.membersOf(
            classNamed("lib.Foo"),
            type -> {
              reads.incrementAndGet();
              return empty();
            });
    final TypeMembers two =
        second.membersOf(
            classNamed("lib.Foo"),
            type -> {
              reads.incrementAndGet();
              return empty();
            });

    assertThat(two).isSameAs(one);
    assertThat(reads).hasValue(1);
  }

  @Test
  void aClassTheUnitDeclaresIsReadFreshAndNeverShared() {
    final JvmClasses project = classes();
    final JvmClasses unit = project.withLocal(Map.of("own.A", classNamed("own.A")));
    final AtomicInteger reads = new AtomicInteger();

    final TypeMembers one =
        unit.membersOf(
            classNamed("own.A"),
            type -> {
              reads.incrementAndGet();
              return empty();
            });
    final TypeMembers two =
        unit.membersOf(
            classNamed("own.A"),
            type -> {
              reads.incrementAndGet();
              return empty();
            });
    final TypeMembers other =
        project.membersOf(
            classNamed("own.A"),
            type -> {
              reads.incrementAndGet();
              return empty();
            });

    assertThat(two).isNotSameAs(one);
    assertThat(other).isNotSameAs(one).isNotSameAs(two);
    assertThat(reads).hasValue(READS_WITHOUT_SHARING);
  }
}
