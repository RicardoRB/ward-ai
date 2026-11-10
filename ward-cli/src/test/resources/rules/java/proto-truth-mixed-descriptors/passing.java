









class Sample {
  Object same(First a) {
    return ProtoTruth.assertThat(a).ignoringFields(First.A_FIELD_NUMBER);
  }

  Object unknown(Object a) {
    return ProtoTruth.assertThat(a).ignoringFields(Second.B_FIELD_NUMBER);
  }
}
