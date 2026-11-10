interface I1 { new (): I1; }
interface I2 { new (): this; }
interface I3 { constructor(): void; }
type T1 = { constructor(): void; };
type T2 = { m(): void; constructor(): void; };
declare class D1 { new(): D1; }
declare class D2 { new(): number; }
interface I4 { new (): number; }
interface I5 { m(): void; constructor(): void; }
