type Hooks = {before: string[]; after: string[]};
declare const name: string;
declare const hooks: Hooks;
const key = name as keyof Hooks;
const list = hooks[key];
type Keys = keyof Hooks;
console.log(list);
export type { Keys };
