type Copy<T> = { [K in keyof T]: T[K] };
type Frozen<T> = { readonly [K in keyof T]?: T[K] };
type Thawed<T> = { -readonly [K in keyof T]-?: T[K] };
type Defined<T> = {
  [K in keyof T as K extends 'id' | 'key' ? K : T[K] extends undefined ? never : K]: T[K];
};
type Flags = { [name in 'a' | 'b']: boolean };

export type { Copy, Frozen, Thawed, Defined, Flags };
