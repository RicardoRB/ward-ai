class Sample {
  @Mock
  Service service;

  Object run() {
    return Mockito.mock(Service.class);
  }
}

class Service {}
