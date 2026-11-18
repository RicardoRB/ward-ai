const c = async () => {
  return await a();
};
async function d() {
  await c();
}

export async function * items(source: AsyncIterable<number>) {
  yield * source;
}
