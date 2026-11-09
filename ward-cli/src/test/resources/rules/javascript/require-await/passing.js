async function c() {
  await a();
}
async function d() {
  await c();
  const f = () => {
    return 1;
  };
  return f();
}
