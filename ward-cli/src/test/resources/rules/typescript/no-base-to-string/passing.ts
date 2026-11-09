





























declare const tagged: { x: number; toString(): string };
const text = `${tagged}`;
const label = String(tagged);
const same = tagged.toString();
