@RequiredModifiers(modifier = Modifier.ABSTRACT)
@interface Abs {}

abstract class T {
  @Abs abstract void run();
}
