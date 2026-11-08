










class Sample {
  @Mock
  Plain plain;

  Object run() {
    return Mockito.mock(Plain.class);
  }
}

class Plain {}
