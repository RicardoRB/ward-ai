import {   join,resolve } from 'node:path';
import * as fs from "node:fs";
import def, { helper } from './helper.js';
export { join as joinPaths };
export default function main(){ return helper(fs) }
export const version = '2.0';
