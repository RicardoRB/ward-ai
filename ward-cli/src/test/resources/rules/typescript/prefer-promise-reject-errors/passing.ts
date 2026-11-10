declare const err: Error;
declare const obj: { err: Error };
declare const cond: boolean;
declare const a: { b: Error };
Promise.reject(new Error("x"));
Promise.reject(err);
Promise.reject(obj.err);
Promise.reject(cond ? new Error() : err);
Promise.reject(a.b);
