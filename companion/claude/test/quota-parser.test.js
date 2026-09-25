import assert from 'node:assert/strict';
import test from 'node:test';
import { parseClaudeUsageOutput, sanitizeTerminalOutput } from '../src/quota-parser.js';

const NOW = new Date('2026-08-23T00:00:00Z');

test('parses current Claude Code session and weekly used percentages', () => {
  const parsed = parseClaudeUsageOutput(`
    Account & Usage
    Current session
    37% used
    Resets in 2h 30m
    Current week (all models)
    54% used
    Resets in 2 days
    Current week (Sonnet only)
    10% used
    Plan: Max 5x
  `, NOW);

  assert.deepEqual(parsed, {
    tier: 'Max 5x',
    windows: [
      { label: '5-Hour', usedFraction: 0.37, resetsAtEpochSeconds: 1787452200 },
      { label: '7-Day', usedFraction: 0.54, resetsAtEpochSeconds: 1787616000 },
      { label: 'Sonnet', usedFraction: 0.1 }
    ]
  });
});

test('converts remaining percentage to used fraction', () => {
  const parsed = parseClaudeUsageOutput(`
    Current session
    63% remaining
    Resets 9:40pm
  `, NOW);

  assert.equal(parsed.windows[0].label, '5-Hour');
  assert.equal(parsed.windows[0].usedFraction, 0.37);
  assert.ok(parsed.windows[0].resetsAtEpochSeconds > Math.floor(NOW.getTime() / 1000));
});

test('parses Windows cursor-spaced quota headings and reset text', () => {
  const rendered = [
    'Account\u001b[C&\u001b[0CUsage',
    'C\u001b[32murrent\u001b[0m \u001b[2Csession',
    '3%used',
    'Resets\u001b[1Cin\u001b[1C2h\u001b[1C30m',
    'Current\u009b1Cweek\u001b[1C(all\u001b[1Cmodels)',
    '0%used',
    'Current\u001b[1Cweek\u001b[1C(Sonnet\u001b[1Conly)',
    '4%used'
  ].join('\r\n');

  assert.deepEqual(parseClaudeUsageOutput(rendered, NOW), {
    windows: [
      { label: '5-Hour', usedFraction: 0.03, resetsAtEpochSeconds: 1787452200 },
      { label: '7-Day', usedFraction: 0 },
      { label: 'Sonnet', usedFraction: 0.04 }
    ]
  });
  assert.equal(sanitizeTerminalOutput('Current\u001b[999999Csession'), 'Current session');
});

test('keeps Windows cursor-spaced stale and incomplete readings unavailable', () => {
  const usage = 'Current\u001b[1Csession\n3%used\n';
  assert.equal(parseClaudeUsageOutput(usage + 'Showing\u001b[1Clast-known\u001b[1Cusage', NOW), null);
  assert.equal(parseClaudeUsageOutput(usage + 'Loading...', NOW), null);
  assert.equal(parseClaudeUsageOutput('Total\u001b[1Ccost:\u001b[1C37%', NOW), null);
});

test('accepts enterprise output with only a current session limit', () => {
  const parsed = parseClaudeUsageOutput('Current session\n12% used\n', NOW);

  assert.deepEqual(parsed, {
    windows: [{ label: '5-Hour', usedFraction: 0.12 }]
  });
});

test('ignores unrelated or invalid percentages and strips terminal controls', () => {
  const sanitized = sanitizeTerminalOutput('\u001b[31mCurrent session\u001b[0m\r150% used');
  assert.equal(sanitized.includes('\u001b'), false);
  assert.equal(parseClaudeUsageOutput(sanitized, NOW), null);
  assert.equal(parseClaudeUsageOutput('Total cost: 37%\n', NOW), null);
});

test('parses CLI weekly calendar resets and keeps each reset with its window', () => {
  const now = new Date(2026, 8, 25, 14, 0);
  const parsed = parseClaudeUsageOutput([
    'Current session', '0% used', 'Resets 6:19pm (Asia/Tokyo)',
    'Current week (all models)', '4% used', 'Resets Sep 29, 3:59pm (Asia/Tokyo)',
    'Extra usage', '0% used'
  ].join('\n'), now);

  assert.deepEqual(parsed.windows, [
    { label: '5-Hour', usedFraction: 0, resetsAtEpochSeconds: new Date(2026, 8, 25, 18, 19).getTime() / 1000 },
    { label: '7-Day', usedFraction: 0.04, resetsAtEpochSeconds: new Date(2026, 8, 29, 15, 59).getTime() / 1000 }
  ]);
});

test('handles year rollover and rejects invalid weekly calendar dates', () => {
  const now = new Date(2026, 11, 31, 14, 0);
  const parseDate = (reset) => parseClaudeUsageOutput(
    `Current session\n0% used\nCurrent week (all models)\n4% used\nResets ${reset}`, now
  ).windows[1].resetsAtEpochSeconds;

  assert.equal(parseDate('Jan 2, 3pm'), new Date(2027, 0, 2, 15, 0).getTime() / 1000);
  assert.equal(parseDate('Feb 30, 3:59pm'), undefined);
  assert.equal(parseDate('Jan 2, 13:59pm'), undefined);
  assert.equal(parseDate('Jan 2, 3:60pm'), undefined);
  assert.equal(parseDate('Sep 29, 3:59pm'), undefined);
});

test('never borrows percentages or resets from another window or extra usage', () => {
  assert.equal(parseClaudeUsageOutput('Current session\nLoading...\nCurrent week (all models)\n4% used', NOW), null);
  assert.equal(parseClaudeUsageOutput('Current session\nCurrent week (all models)\n4% used', NOW), null);
  assert.equal(parseClaudeUsageOutput('Current session\n0% used\nCurrent week (all models)\nExtra usage\n4% used', NOW), null);
  const parsed = parseClaudeUsageOutput('Current session\n0% used\nCurrent week (all models)\n4% used\nResets in 2 days', NOW);
  assert.equal(parsed.windows[0].resetsAtEpochSeconds, undefined);
  assert.equal(parsed.windows[1].resetsAtEpochSeconds, Math.floor(NOW.getTime() / 1000) + 2 * 24 * 60 * 60);
});

test('waits for loading to finish and never forwards arbitrary plan text', () => {
  assert.equal(
    parseClaudeUsageOutput('Account & Usage\nCurrent session\n37% used\nLoading...', NOW),
    null
  );
  assert.deepEqual(
    parseClaudeUsageOutput('Account & Usage\nCurrent session\n37% used\nPlan: private-hook-output', NOW),
    { windows: [{ label: '5-Hour', usedFraction: 0.37 }] }
  );
});

test('rejects last-known usage instead of publishing it as a fresh reading', () => {
  assert.equal(parseClaudeUsageOutput(`Showing last-known usage
Current session
  38% used
  Resets 9pm (Asia/Tokyo)`), null);
  assert.equal(parseClaudeUsageOutput(`Current session
  38% used
  Showing last-known usage`), null);
});
