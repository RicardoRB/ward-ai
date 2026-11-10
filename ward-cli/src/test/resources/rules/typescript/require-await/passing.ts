const c = async () => {
  return await a();
};
async function d() {
  await c();
}
