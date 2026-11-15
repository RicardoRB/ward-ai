let finish!: () => void;
const ready = new Promise<void>(resolve => {
  finish = resolve;
});
finish();
await ready;
