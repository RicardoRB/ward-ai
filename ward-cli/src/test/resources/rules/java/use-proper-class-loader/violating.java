class Loaders {
  ClassLoader loader() {
    return Widget.class.getClassLoader();
  }
}

class Widget {}
