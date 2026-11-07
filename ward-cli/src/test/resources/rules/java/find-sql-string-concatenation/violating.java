final class Check {
  Object use(Object session, Object secret) throws Exception {
    return session.createSQLQuery("select * from t where id = " + secret);
  }
}
