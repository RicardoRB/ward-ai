interface Box<in out T> {
  value: T;
}
type Reader<out T> = { get(): T };
