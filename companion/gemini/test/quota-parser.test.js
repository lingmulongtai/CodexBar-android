import test from 'node:test';
import assert from 'node:assert/strict';
import { parseGeminiQuotaOutput, sanitizeTerminalOutput } from '../src/quota-parser.js';

test('parses only sanitized quota rows from official CLI output', () => {
  const now = new Date('2026-07-16T12:00:00Z');
  const output = `
Tier: Google AI Pro
────────────────────────────────────────────────────────────────────────
Model usage

Pro         ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬    25%  Resets: 10:30 PM (1h 30m)
Flash Lite  ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬    10%  Resets: 11:00 PM (2h)
`;

  const result = parseGeminiQuotaOutput(output, now);

  assert.deepEqual(result, {
    tier: 'Google AI Pro',
    windows: [
      { label: 'Pro', usedFraction: 0.25, resetsAtEpochSeconds: 1784208600 },
      { label: 'Flash Lite', usedFraction: 0.1, resetsAtEpochSeconds: 1784210400 }
    ]
  });
});

test('does not produce a snapshot from unrelated terminal content', () => {
  assert.equal(parseGeminiQuotaOutput('email@example.test\nPrompt text 25%\n'), null);
});

test('strips ANSI control sequences without logging raw terminal state', () => {
  assert.equal(sanitizeTerminalOutput('\u001b[31mPro\u001b[0m'), 'Pro');
});
