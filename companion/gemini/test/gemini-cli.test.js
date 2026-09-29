import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { collectGeminiQuota } from '../src/gemini-cli.js';

function removeTestHome(homeDirectory) {
  if (path.dirname(homeDirectory) !== fs.realpathSync(os.tmpdir()) ||
      !path.basename(homeDirectory).startsWith('codexbar-gemini-')) throw new Error('Unsafe test directory');
  fs.rmSync(homeDirectory, { recursive: true });
}

test('requests the official model quota view without sending an inference prompt', async () => {
  const homeDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'codexbar-gemini-'));
  const writes = [];
  let onData;
  const spawn = (_command, args, options) => {
    assert.deepEqual(args, ['--screen-reader']);
    assert.equal(options.cwd, path.join(homeDirectory, '.codexbar', 'gemini-workspace'));
    return {
      onData: callback => { onData = callback; setTimeout(() => callback('Type your message or @path/to/file'), 0); },
      onExit: () => {},
      write: value => {
        writes.push(value);
        onData('Model usage\nPro  25%  Resets: 10:30 PM (1h 30m)\n');
      },
      kill: () => {}
    };
  };
  const result = await collectGeminiQuota({ command: 'gemini.cmd', startupDelayMillis: 1,
    timeoutMillis: 2000, now: new Date('2026-07-16T12:00:00Z'), homeDirectory, spawn });
  assert.deepEqual(writes, ['/stats model\r']);
  assert.equal(result.windows[0].usedFraction, 0.25);
  removeTestHome(homeDirectory);
});

test('does not confirm Gemini CLI authentication or folder-trust prompts', async () => {
  const homeDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'codexbar-gemini-'));
  let writes = 0;
  const spawn = () => ({
    onData: callback => setTimeout(() => callback('Do you trust the files in this folder?'), 0),
    onExit: () => {},
    write: () => { writes++; },
    kill: () => {}
  });
  await assert.rejects(collectGeminiQuota({ startupDelayMillis: 10, timeoutMillis: 2000, homeDirectory, spawn }),
    /Complete Gemini CLI sign-in and folder trust/);
  assert.equal(writes, 0);
  removeTestHome(homeDirectory);
});
