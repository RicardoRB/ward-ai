import { order } from "@app/domain/order.js";
import { util } from "./util";
import { table } from "./data";
import type { Id } from "./types.js";
import { lib } from "lib";
import { one } from "lib/feature/one";
import { readFile } from "node:fs";
import React from "react";

export const app = { order, util, table, lib, one, readFile, React };
export type AppId = Id;
