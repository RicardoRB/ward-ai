function * inner(): Generator<number> {
  yield 1;
}

function * outer(): Generator<number> {
  yield * inner();
  yield* inner();
}

const each = (async function * <T, R>(items: T[], extra?: R): AsyncIterableIterator<T> {
  yield * items;
});

console.log(outer, each);
