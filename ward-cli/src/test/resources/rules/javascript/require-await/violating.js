async function a() {
  return 1;
}
const b = async () => {
  console.log(1);
};
async function c() {
  await a();
}
