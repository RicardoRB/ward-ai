class Sample {
  Object mixed(First a) {
    return ProtoTruth.assertThat(a).ignoringFields(First.A_FIELD_NUMBER, Second.B_FIELD_NUMBER);
  }

  Object wrongSubject(First a) {
    return ProtoTruth.assertThat(a).ignoringFields(Second.B_FIELD_NUMBER);
  }
}
