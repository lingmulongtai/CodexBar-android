import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { collectCodexTelemetry } from '../src/scanner.js';

test('aggregates only token metadata and derives current context usage', async (context) => {
  const home = await fs.mkdtemp(path.join(os.tmpdir(), 'codexbar-codex-scanner-'));
  context.after(() => fs.rm(home, { recursive: true, force: true }));
  const sessions = path.join(home, 'sessions', '2026', '08', '13');
  await fs.mkdir(sessions, { recursive: true });
  const records = [
    {
      timestamp: '2026-08-13T01:00:00.000Z',
      type: 'turn_context',
      payload: { model: 'gpt-5.6', cwd: 'must-not-leak', user_prompt: 'must-not-leak' }
    },
    {
      timestamp: '2026-08-13T01:01:00.000Z',
      type: 'event_msg',
      payload: {
        type: 'token_count',
        info: {
          total_token_usage: tokenTotals(100, 20, 10, 3),
          last_token_usage: tokenTotals(100, 20, 10, 3),
          model_context_window: 1_000
        }
      }
    },
    {
      timestamp: '2026-08-13T01:02:00.000Z',
      type: 'event_msg',
      payload: {
        type: 'token_count',
        info: {
          total_token_usage: tokenTotals(160, 40, 20, 5),
          last_token_usage: tokenTotals(60, 20, 10, 2),
          model_context_window: 1_000
        }
      }
    },
    {
      timestamp: '2026-08-13T01:03:00.000Z',
      type: 'response_item',
      payload: { type: 'message', text: 'private conversation must not be parsed or served' }
    }
  ];
  await fs.writeFile(
    path.join(sessions, 'rollout.jsonl'),
    `${records.map((record) => JSON.stringify(record)).join('\n')}\n`,
    'utf8'
  );

  const snapshot = await collectCodexTelemetry({
    codexHome: home,
    now: new Date('2026-08-13T02:00:00.000Z')
  });

  assert.equal(snapshot.currentContext.model, 'gpt-5.6');
  assert.equal(snapshot.currentContext.usedTokens, 70);
  assert.equal(snapshot.currentContext.contextWindowTokens, 1_000);
  assert.equal(snapshot.tokenUsage.today.totalTokens, 180);
  assert.equal(snapshot.tokenUsage.today.cachedInputTokens, 40);
  assert.deepEqual(snapshot.tokenUsage.models.map((entry) => entry.model), ['gpt-5.6']);
  assert.equal(JSON.stringify(snapshot).includes('must-not-leak'), false);
  assert.equal(JSON.stringify(snapshot).includes('private conversation'), false);
});

test('deduplicates copied token events and ignores malformed or oversized deltas', async (context) => {
  const home = await fs.mkdtemp(path.join(os.tmpdir(), 'codexbar-codex-scanner-'));
  context.after(() => fs.rm(home, { recursive: true, force: true }));
  const sessions = path.join(home, 'sessions');
  const archived = path.join(home, 'archived_sessions');
  await fs.mkdir(sessions, { recursive: true });
  await fs.mkdir(archived, { recursive: true });
  const duplicate = JSON.stringify({
    timestamp: '2026-08-12T23:00:00.000Z',
    type: 'event_msg',
    payload: {
      type: 'token_count',
      info: {
        model: 'gpt-5.6',
        last_token_usage: tokenTotals(50, 10, 5, 1),
        model_context_window: 2_000
      }
    }
  });
  await fs.writeFile(path.join(sessions, 'a.jsonl'), `${duplicate}\nnot-json\n`, 'utf8');
  await fs.writeFile(path.join(archived, 'copy.jsonl'), `${duplicate}\n`, 'utf8');

  const snapshot = await collectCodexTelemetry({
    codexHome: home,
    now: new Date('2026-08-13T02:00:00.000Z')
  });

  assert.equal(snapshot.tokenUsage.last7Days.totalTokens, 55);
  assert.equal(snapshot.tokenUsage.daily.length, 1);
});

function tokenTotals(input, cached, output, reasoning) {
  return {
    input_tokens: input,
    cached_input_tokens: cached,
    output_tokens: output,
    reasoning_output_tokens: reasoning,
    total_tokens: input + output
  };
}
