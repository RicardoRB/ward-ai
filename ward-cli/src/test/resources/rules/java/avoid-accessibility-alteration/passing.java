import java.lang.reflect.Field;

class Access {
  Object read(Field field, Object target) throws IllegalAccessException {
    return field.get(target);
  }
}

class OwnHelper {
  private boolean setAccessible(boolean force, Object member) {
    return force && member != null;
  }

  boolean open(Object member) {
    return setAccessible(true, member) && this.setAccessible(true, member);
  }
}

class StaticHelper {
  boolean open(java.lang.reflect.Field field) {
    return Helpers.setAccessible(true, field) && java.util.Objects.nonNull(field);
  }
}
