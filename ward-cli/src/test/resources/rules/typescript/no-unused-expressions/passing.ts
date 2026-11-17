"use strict";
a = 1;
f();
new X();
a++;
delete a.b;
void 0;
async function g() { await p; }

export function adjust(holder: {count?: number; agent?: number}, options: {agent?: number | false}) {
  holder.agent = options.agent === false ? 0 : (options.agent as number | undefined) ?? 1;
}
