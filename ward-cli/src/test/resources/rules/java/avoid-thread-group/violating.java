class Groups {
  ThreadGroup a() {
    return new ThreadGroup("workers");
  }
  ThreadGroup b() {
    return Thread.currentThread().getThreadGroup();
  }
}
