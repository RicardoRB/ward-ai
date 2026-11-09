import { useState } from 'react';
import { gone } from 'lodash';
import type { Config } from '@acme/config';
import { fs } from 'node:fs';

export const value = useState ?? gone ?? fs;
export type Shape = Config;
