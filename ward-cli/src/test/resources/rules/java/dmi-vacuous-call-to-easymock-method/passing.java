// passes
final class Check {
  void run(Object mock) {
    org.easymock.EasyMock.replay(mock);
    other.EasyMock.replay();
  }
}
