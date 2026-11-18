const a = "\n\t\\\"";
const b = 'it\'s';
const c = `\`\${x}`;
const d = /\d\/\.\[\]\\/;
const e = /[\]\-\\]/;
const f = /\$\^\b/;

export const header = String.raw`<https://example.com>; title="Chapter \"1\""`;
