declare const counter: { label: string; reset(): void };
const bound = counter.reset;
counter.reset();
