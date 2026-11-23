globalThis.clearTimeout = timeout => { if (timeout) { clear(timeout); } };
const loop = (items: number[]) => { for (const item of items) { use(item); } };
const pick = (flag: boolean) => { while (flag) { flag = false; } };
