let label;
let total = 0;
label ||= 'none';
label &&= label.trim();
total ??= 1;
console.log(label, total);
