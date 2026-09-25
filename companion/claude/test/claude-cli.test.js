import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, rmdirSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { ClaudeUsageSession } from '../src/claude-cli.js';

test('uses a persistent dedicated workspace and reuses the Claude PTY for stable usage', async (t) => {
  const homeDirectory = mkdtempSync(path.join(os.tmpdir(), 'codexbar-claude-test-'));
  const workspaceDirectory = path.join(homeDirectory, '.codexbar', 'claude-workspace');
  t.after(() => {
    if (existsSync(workspaceDirectory)) rmdirSync(workspaceDirectory);
    if (existsSync(path.dirname(workspaceDirectory))) rmdirSync(path.dirname(workspaceDirectory));
    rmdirSync(homeDirectory);
  });
  let spawnCount = 0;
  let killed = false;
  const writes = [];
  let dataHandler = () => {};
  let exitHandler = () => {};
  const terminal = {
    onData(handler) {
      dataHandler = handler;
    },
    onExit(handler) {
      exitHandler = handler;
    },
    write(value) {
      writes.push(value);
      if (value !== '/usage\r') return;
      setTimeout(() => dataHandler('Account & Usage\nCurrent session\nLoading...\n'), 1);
      setTimeout(() => dataHandler(
        'Account & Usage\nCurrent session\n25% used\nCurrent week (all models)\n40% used\nPlan: Pro\n'
      ), 8);
    },
    kill() {
      killed = true;
      exitHandler({ exitCode: 0 });
    }
  };
  const session = new ClaudeUsageSession({
    spawn(command, args, options) {
      spawnCount += 1;
      assert.equal(command, 'claude-test');
      assert.deepEqual(args, ['--allowed-tools', '']);
      assert.equal(options.cwd, workspaceDirectory);
      assert.notEqual(options.cwd, homeDirectory);
      assert.equal(existsSync(workspaceDirectory), true);
      return terminal;
    },
    command: 'claude-test',
    homeDirectory,
    startupDelayMillis: 0,
    commandDelayMillis: 0,
    timeoutMillis: 500,
    parseSettleMillis: 15,
    minimumObservationMillis: 20
  });
  t.after(() => session.close());

  const first = await session.collect({ now: new Date('2026-08-23T00:00:00Z') });
  const second = await session.collect({ now: new Date('2026-08-23T00:05:00Z') });

  assert.equal(spawnCount, 1);
  assert.equal(writes.filter((value) => value === '/usage\r').length, 2);
  assert.deepEqual(first, second);
  assert.deepEqual(first, {
    tier: 'Pro',
    windows: [
      { label: '5-Hour', usedFraction: 0.25 },
      { label: '7-Day', usedFraction: 0.4 }
    ]
  });
  assert.equal(killed, false);

  session.close();
  assert.equal(killed, true);
  assert.equal(existsSync(workspaceDirectory), true);
});
