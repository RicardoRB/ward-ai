async function run(): Promise<void> {
  const a = await f();
  g(a);
}
