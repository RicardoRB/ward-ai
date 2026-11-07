@RequiredModifiers(modifier = Modifier.ABSTRACT)
@interface Abs {}

abstract class T {
  @Abs void run() {}
}
