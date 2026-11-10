const a = <div><><span /><b /></></div>;
const b = <><><span /></></>;
const d = <><>x</></>;
const e = <><>x</>y</>;
function h() { return <><span /></>; }
const j = <p x={<><div /></>} />;
const k = <p>{<><div /></>}</p>;
const l = a && <><div /></>;
const q = <div><><span /></></div>;
const s = <><>{a}</></>;
const t = <div><>{a}</></div>;
const u = <div>{a && <><b /></>}</div>;
const y = <div><><b /></></div>;
const z = <><><b /><i /></></>;
const e1 = <div><Fragment><span /></Fragment></div>;
const e2 = <div><React.Fragment><span /></React.Fragment></div>;
const e3 = <div><F><span /></F></div>;
import { Fragment, Fragment as F } from 'react';
import * as React from 'react';
