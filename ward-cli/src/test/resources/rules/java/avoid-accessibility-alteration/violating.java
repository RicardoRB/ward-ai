import java.lang.reflect.Field;

class Access {
  void open(Field field) {
    field.setAccessible(true);
  }
}
