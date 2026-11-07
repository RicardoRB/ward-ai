final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return new GCMParameterSpec(128, "0123456789ab".getBytes());
  }
}
