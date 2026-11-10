Promise.reject(new Error("x"));
Promise.reject(err);
Promise.reject(obj.err);
Promise.reject(cond ? new Error() : err);
Promise.reject(a?.b);
