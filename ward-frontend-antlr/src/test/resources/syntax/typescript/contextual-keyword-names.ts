const get = 1, set = 2, using = 3, asserts = 4, infer = 5, out = 6, global = 7
const satisfies = 8, accessor = 9, is = 10
run(get, set, using, asserts, infer, out, global, satisfies, accessor, is)

const holder = { get: 1, set: 2, is: 3, using: 4 }
holder.is(holder.get)
view.is(effect)

async function wait() {
  await pending
  await using handle = open()
  handle.close()
}

function guard(x: unknown): asserts x is string {}
function check(x: unknown): x is string { return true }
type Item<T> = T extends Array<infer E> ? E : never
interface Box<in A, out B> {}
declare global { interface Window { extra: number } }
