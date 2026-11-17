import type { Shape } from './shape';
import { helper } from './helper';

export function area(shape: Shape): number {
  return helper(shape);
}

import Widget, {type WidgetOptions} from './widget.js';

export const widget = new Widget({} as WidgetOptions);

import Options, {type Settings} from './options.js';

export const options = new Options({} as Settings);
