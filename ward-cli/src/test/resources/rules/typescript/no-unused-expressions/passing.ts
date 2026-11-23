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

export function cancelAll(jobs: Array<{cancel?: (error: Error) => void}>, error: Error) {
  for (const job of jobs) {
    job.cancel?.(error);
  }
}

export function pick(holder: {agent?: object}, cond: boolean, fallback: object, options: {agent?: object}) {
  holder.agent = cond ? {max: 0} : options.agent as object | undefined ?? fallback;
}
