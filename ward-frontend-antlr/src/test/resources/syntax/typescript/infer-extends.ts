type First<T> = T extends [infer H extends string, ...unknown[]] ? H : never;
