final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return String.format("select * from t where id = %s", secret);
  }
}
