const c = <div><>x</></div>;
function i() { return <>x</>; }
const m = () => <><div /></>;
const n = { o: <><div /></> };
const p = <><div /></>;
const r = <>{a}</>;
const v = <><span /><b /></>;
const w = <p>{<>x</>}</p>;
const f1 = <div><Fragment key="a"><span /></Fragment></div>;
const f2 = <div><Fragment><span /><b /></Fragment></div>;
const f3 = <div><Shell><span /></Shell></div>;
const f4 = <div><Fragment><span /></Fragment></div>;
import { Fragment } from './shell';
