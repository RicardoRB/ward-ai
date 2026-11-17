const values = [1, undefined, 3];

export const [, second] = [1, 2];
export const [head, , tail] = [1, 2, 3];
export const pairs = Object.entries({a: 1}).filter(([, value]) => value !== undefined);
