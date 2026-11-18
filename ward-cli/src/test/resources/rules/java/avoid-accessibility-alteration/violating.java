import java.lang.reflect.Field;

class Access {
  void open(Field field) {
    field.setAccessible(true);
  }
}

class Bulk {
  void open(java.lang.reflect.Field[] fields) {
    java.lang.reflect.AccessibleObject.setAccessible(fields, true);
  }
}
