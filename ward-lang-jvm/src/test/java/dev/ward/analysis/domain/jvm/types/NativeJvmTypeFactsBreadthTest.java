package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Types a file that uses most forms of Java, to check that none of them breaks the others. */
class NativeJvmTypeFactsBreadthTest {
  private static final String HELPERS =
      "package app;\npublic interface Shape { double area(); default String label() { return \"s\"; } }\n";
  private static final String KINDS =
      "package app;\npublic enum Kind { A, B; static Kind first() { return A; } }\n";
  private static final String POINT =
      "package app;\npublic record Point(int x, int y) implements Shape { public double area() { return 0; } }\n";

  private static final String SOURCE =
      """
      package app;
      import static java.lang.Math.max;
      import java.util.*;
      import java.util.function.*;
      import java.io.*;
      @SuppressWarnings("all")
      public class App<T extends Number> extends Object implements Comparable<App<T>>, Serializable {
        static final int LIMIT = 0x10 + 010 + 0b11;
        static final long BIG = 10_000L * 2;
        static final double RATIO = 1.5e2 / 2;
        static final float SMALL = 2.5f;
        static final String TEXT = "a" + LIMIT + 'c';
        static final char LETTER = 'x';
        static final boolean FLAG = !false;
        T value;
        int[][] grid = new int[2][3];
        List<? extends Number> numbers;
        public int compareTo(App<T> other) { return 0; }
        <R> R convert(Function<T, R> function) { return function.apply(value); }
        static int sum(int... values) { return values.length; }
        void run(Shape shape, Kind kind, Point point, Object any, String name) throws IOException {
          int a = max(1, 2);
          long b = a + BIG;
          double c = a / 2.0 - b * 3 % 2;
          boolean d = a > 1 && b < 2 || c >= 3 ^ !FLAG;
          int e = d ? 1 : 2;
          Integer f = d ? null : 3;
          Object g = d ? "s" : Integer.valueOf(1);
          int h = a << 2 >> 1 >>> 1;
          int i = -a + +e - ~h;
          a += 2; a++; --a;
          String s1 = name + a + 1.5 + 'c' + true;
          var list = new ArrayList<String>();
          list.add("x");
          for (String item : list) { s1 = item.trim(); }
          for (int idx = 0; idx < 3; idx++) { grid[idx][0] = idx; }
          try (BufferedReader reader = new BufferedReader(new StringReader(s1))) {
            String line = reader.readLine();
          } catch (IOException | IllegalStateException failure) {
            Throwable t = failure;
          } finally {
            a = 0;
          }
          switch (kind) { case A: a = 1; break; default: a = 2; }
          int r = switch (kind) { case A -> 1; case B -> 2; };
          String desc = switch (a) { case 1 -> "one"; default -> { yield "many"; } };
          if (any instanceof String text && !text.isEmpty()) { text.length(); }
          Runnable run = () -> System.out.println(s1);
          Supplier<String> supplier = () -> { return s1; };
          BiFunction<Integer, Integer, Integer> add = (x, y) -> x + y;
          Comparator<String> byLength = Comparator.comparing(String::length);
          Map<String, List<Integer>> map = new HashMap<>();
          map.computeIfAbsent("k", key -> new ArrayList<>()).add(1);
          Optional<String> maybe = Optional.of(name).map(String::trim).filter(v -> !v.isEmpty());
          String joined = String.join(",", list);
          int total = sum(1, 2, 3) + sum() + sum(new int[] {1});
          Class<?> type = String.class;
          Object copy = (Object) shape;
          double area = shape.area() + point.x() + kind.ordinal() + Kind.first().ordinal();
          String label = shape.label() + Kind.A + new Point(1, 2).y();
          Object anonymous = new Object() { public String toString() { return "x"; } };
          this.value = null;
          App.this.grid = null;
          int length = grid.length + grid[0].length;
          char ch = LETTER;
          long cast = (long) a + (int) c;
          assert a > 0 : "positive";
          synchronized (this) { a++; }
          label: while (a < 10) { a++; if (a > 5) break label; else continue; }
          do { a--; } while (a > 0);
          throw new IOException("x");
        }
        interface Inner { int go(); }
        static class Nested<K, V extends Comparable<V>> { K key; V val; Nested(K k) { key = k; } }
        enum Mode { ON(1), OFF(2); final int code; Mode(int code) { this.code = code; } }
        record Pair<L, R>(L left, R right) { Pair { } }
        @interface Marker { String value() default "x"; }
      }
      """;

  @Test
  void typesTheFormsOfJavaWithoutLosingTheKnownOnes() {
    final Probe probe =
        new Probe(SOURCE, Map.of("app.Shape", HELPERS, "app.Kind", KINDS, "app.Point", POINT));

    assertThat(probe.type("max(1, 2)")).isEqualTo("int");
    assertThat(probe.type("a + BIG")).isEqualTo("long");
    assertThat(probe.type("d ? 1 : 2")).isEqualTo("int");
    assertThat(probe.type("name + a + 1.5 + 'c' + true")).isEqualTo("java.lang.String");
    assertThat(probe.type("new ArrayList<String>()"))
        .isEqualTo("java.util.ArrayList<java.lang.String>");
    assertThat(probe.type("item.trim()")).isEqualTo("java.lang.String");
    assertThat(probe.type("reader.readLine()")).isEqualTo("java.lang.String");
    assertThat(probe.type("shape.area()")).isEqualTo("double");
    assertThat(probe.type("point.x()")).isEqualTo("int");
    assertThat(probe.type("kind.ordinal()")).isEqualTo("int");
    assertThat(probe.type("Kind.first()")).isEqualTo("app.Kind");
    assertThat(probe.type("String.join(\",\", list)")).isEqualTo("java.lang.String");
    assertThat(probe.type("grid.length")).isEqualTo("int");
    assertThat(probe.type("String.class")).isEqualTo("java.lang.Class<java.lang.String>");
    assertThat(probe.type("(long) a + (int) c")).isEqualTo("long");
  }
}
