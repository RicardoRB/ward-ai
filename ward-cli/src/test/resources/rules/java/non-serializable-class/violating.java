class Sample implements java.io.Serializable {
  Helper helper;
  transient Helper skipped;
  static Helper shared;
  int count;
}
