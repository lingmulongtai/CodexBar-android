#!/usr/bin/env node
import fs from 'node:fs';
import { PROVIDERS, readIdentity } from './providers.js';
import { fetchSnapshot, projectSnapshot } from './snapshot-client.js';

const address = process.argv[2];
if (!address) process.exit(2);
const entries = await Promise.all(Object.entries(PROVIDERS).map(async ([key, definition]) => {
  try {
    const identity = JSON.parse(fs.readFileSync(readIdentity(key), 'utf8'));
    const snapshot = await fetchSnapshot({ provider: key, address, port: definition.port, identity });
    return [key, { state: 'ready', ...projectSnapshot(key, snapshot) }];
  } catch (error) {
    const code = error?.code === 'ENOENT' ? 'NOT_PAIRED' :
      error?.message === 'Bridge has no fresh snapshot' ? 'SOURCE_UNAVAILABLE' : 'BRIDGE_UNAVAILABLE';
    return [key, { state: 'unavailable', code }];
  }
}));
process.stdout.write(`${JSON.stringify(Object.fromEntries(entries))}\n`);
