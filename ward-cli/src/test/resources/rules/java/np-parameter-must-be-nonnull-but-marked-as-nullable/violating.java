class Sample {
  int length(@Nullable String text) {
    return text.length();
  }
}


class Containers {
  int map(@Nullable Map<String, Object> properties) {
    return properties.size();
  }

  int array(String @Nullable [] items) {
    return items.length;
  }
}
