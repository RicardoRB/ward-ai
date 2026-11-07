import java.lang.reflect.Field;

class Access {
  Object read(Field field, Object target) throws IllegalAccessException {
    return field.get(target);
  }
}
