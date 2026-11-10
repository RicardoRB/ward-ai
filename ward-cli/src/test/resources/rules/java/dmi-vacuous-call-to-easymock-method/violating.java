final class Check {
  void run() {
    org.easymock.EasyMock.replay();
    org.easymock.EasyMock.verify();
    org.easymock.EasyMock.resetToNice();
  }
}
