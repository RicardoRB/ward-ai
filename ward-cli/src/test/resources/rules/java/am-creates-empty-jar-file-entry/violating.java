class Sample {
  void run(java.util.jar.JarOutputStream out) throws Exception {
    out.putNextEntry(new java.util.jar.JarEntry("a"));
    out.closeEntry();
  }
}
