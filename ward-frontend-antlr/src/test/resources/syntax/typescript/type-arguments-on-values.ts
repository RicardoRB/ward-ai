const real = await importOriginal<typeof import('some-module')>();
const make = createThing<Config>;
const keyed = createThing<Config, Other>;
class Listener {
  @Pattern<Events>('topic')
  handle() {}
}
let loaded: typeof import('some-module');
let named: import('some-module').Shape<string>;
let nested: typeof import('some-module').helper;
expectTypeOf(createWithContext<CustomApplication>).returns.toEqualTypeOf<
  Promise<CustomApplication>
>();
register(createThing<Config>, other<Item>.length);
const less = a < b;
const both = a < b && c > d;
const shifted = x << 2 >> 1;
