import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { loadOrCreateIdentity } from '../src/config.js';

test('creates one persistent private pairing identity', (context) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'codexbar-gemini-'));
  context.after(() => fs.rmSync(home, { recursive: true, force: true }));

  const first = loadOrCreateIdentity(home);
  const second = loadOrCreateIdentity(home);

  assert.deepEqual(second, first);
  assert.equal(Buffer.from(first.sharedKey, 'base64url').length, 32);
  const configPath = path.join(home, '.codexbar', 'gemini-companion.json');
  assert.equal(fs.existsSync(configPath), true);
  if (process.platform !== 'win32') {
    assert.equal(fs.statSync(configPath).mode & 0o077, 0);
  }
});
