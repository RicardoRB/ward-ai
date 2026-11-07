class Sample {
  void run(java.util.zip.ZipOutputStream out) throws Exception {
    out.putNextEntry(new java.util.zip.ZipEntry("a"));
    out.write(1);
    out.closeEntry();
  }
}
