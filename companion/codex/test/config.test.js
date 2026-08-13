import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { loadOrCreateIdentity } from '../src/config.js';

test('persists and reuses a private companion identity', async (context) => {
  const home = await fs.mkdtemp(path.join(os.tmpdir(), 'codexbar-codex-config-'));
  context.after(() => fs.rm(home, { recursive: true, force: true }));

  const first = loadOrCreateIdentity(home);
  const second = loadOrCreateIdentity(home);

  assert.deepEqual(second, first);
  assert.match(first.companionId, /^[0-9a-f-]{36}$/i);
  assert.match(first.sharedKey, /^[A-Za-z0-9_-]{43}$/);
});
