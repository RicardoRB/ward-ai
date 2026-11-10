package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassFileReaderTest {
  private static final int SHORT_HEADER = 8;

  static JvmClass read(final Class<?> type) throws IOException, ClassFileException {
    try (InputStream in =
        ClassLoader.getSystemResourceAsStream(type.getName().replace('.', '/') + ".class")) {
      return ClassFileReader.read(in.readAllBytes());
    }
  }

  @Test
  void readsAGenericInterface() throws IOException, ClassFileException {
    final JvmClass map = read(java.util.Map.class);

    assertThat(map.name()).isEqualTo("java.util.Map");
    assertThat(map.kind()).isEqualTo(JvmClass.Kind.INTERFACE);
    assertThat(map.isAbstract()).isTrue();
    assertThat(map.typeParameters()).extracting(TypeParameter::name).containsExactly("K", "V");
    assertThat(map.memberClasses()).contains("java.util.Map$Entry");
    final JvmMethod getOrDefault =
        map.methods().stream()
            .filter(m -> "getOrDefault".equals(m.name()))
            .findFirst()
            .orElseThrow();
    assertThat(getOrDefault.parameters())
        .containsExactly(JType.ClassType.of("java.lang.Object"), new JType.TypeVariable("V"));
    assertThat(getOrDefault.returnType()).isEqualTo(new JType.TypeVariable("V"));
  }

  @Test
  void readsSuperclassInterfacesAndNestedArguments() throws IOException, ClassFileException {
    final JvmClass list = read(java.util.ArrayList.class);

    assertThat(list.superclass())
        .get()
        .extracting(JType.ClassType::name)
        .isEqualTo("java.util.AbstractList");
    assertThat(list.interfaces()).extracting(JType.ClassType::name).contains("java.util.List");
    assertThat(list.interfaces().get(0).arguments()).containsExactly(new JType.TypeVariable("E"));
  }

  @Test
  void readsConstantsThrownExceptionsAndSealing() throws IOException, ClassFileException {
    final JvmClass integer = read(Integer.class);
    final JvmField max =
        integer.fields().stream()
            .filter(f -> "MAX_VALUE".equals(f.name()))
            .findFirst()
            .orElseThrow();
    assertThat(max.constant()).contains(Integer.MAX_VALUE + 0L);
    assertThat(max.isStatic()).isTrue();

    final JvmClass closeable = read(java.io.Closeable.class);
    final JvmMethod close = closeable.methods().get(0);
    assertThat(close.thrown()).containsExactly(JType.ClassType.of("java.io.IOException"));

    assertThat(read(java.lang.constant.ConstantDesc.class).sealed()).isTrue();
    assertThat(read(Thread.State.class).kind()).isEqualTo(JvmClass.Kind.ENUM);
    assertThat(read(Override.class).kind()).isEqualTo(JvmClass.Kind.ANNOTATION);
    assertThat(read(Override.class).annotations()).contains("java.lang.annotation.Target");
  }

  @Test
  void readsRecordsAndStringConstants() throws IOException, ClassFileException {
    assertThat(read(java.lang.runtime.ObjectMethods.class).kind()).isEqualTo(JvmClass.Kind.CLASS);
    assertThat(read(RecordFixture.class).kind()).isEqualTo(JvmClass.Kind.RECORD);
    assertThat(read(RecordFixture.class).fields())
        .filteredOn(f -> "TEXT".equals(f.name()))
        .extracting(f -> f.constant().orElseThrow())
        .containsExactly("héllo");
  }

  @Test
  void rejectsBytesThatAreNotAClassFile() {
    assertThatThrownBy(() -> ClassFileReader.read(new byte[SHORT_HEADER]))
        .isInstanceOf(ClassFileException.class);
    assertThatThrownBy(() -> ClassFileReader.read(new byte[1]))
        .isInstanceOf(ClassFileException.class);
  }

  /** A record with a string constant outside ASCII. */
  record RecordFixture(List<String> names) {
    static final String TEXT = "héllo";
  }
}
