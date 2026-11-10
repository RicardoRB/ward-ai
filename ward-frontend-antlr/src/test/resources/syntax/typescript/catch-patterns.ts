function run(work: () => void): void {
  try {
    work();
  } catch ({ message }) {
    console.log(message);
  }
  try {
    work();
  } catch ([first, second]: string[]) {
    console.log(first, second);
  }
  try {
    work();
  } catch (error: unknown) {
    console.log(error);
  }
}
