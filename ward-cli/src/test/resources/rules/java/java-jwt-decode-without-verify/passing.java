final class Check {
  Object use(Object verifier, Object secret) throws Exception {
    return verifier.verify("token");
  }
}
