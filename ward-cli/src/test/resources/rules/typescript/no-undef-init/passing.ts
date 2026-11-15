let value;
declare const other: string | undefined;
const present = other !== undefined;
const absent = other === undefined;
console.log(value, present, absent);
