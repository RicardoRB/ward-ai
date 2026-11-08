import com.google.auto.value.AutoValue;

@AutoValue
abstract class Person {
  abstract String name();

  abstract int age();

  static Person ordered(String name, int age) {
    return new AutoValue_Person(name, age);
  }

  static Person computed(String name, int age) {
    return new AutoValue_Person(age, name.trim());
  }

  static Person other(String name, int years) {
    return new AutoValue_Person(years, name);
  }
}
