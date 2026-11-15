final class Check {
  boolean same(String name) {
    return name.equals("red");
  }



  boolean lists(java.util.List<Object> colors) {
    return colors.equals(java.util.List.of(Color.RED));
  }
}
