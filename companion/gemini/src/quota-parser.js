const ANSI_PATTERN = /[\u001B\u009B][[\]()#;?]*(?:(?:(?:[a-zA-Z\d]*(?:;[-a-zA-Z\d\/#&.:=?%@~_]+)*)?\u0007)|(?:(?:\d{1,4}(?:[;:]\d{0,4})*)?[\dA-PR-TZcf-nq-uy=><~]))/g;
const MAX_CAPTURE_LENGTH = 256 * 1024;
const MAX_WINDOWS = 8;

export function sanitizeTerminalOutput(value) {
  return value
    .slice(-MAX_CAPTURE_LENGTH)
    .replace(ANSI_PATTERN, '')
    .replace(/\u0008/g, '')
    .replace(/\r(?!\n)/g, '\n');
}

export function parseGeminiQuotaOutput(output, now = new Date()) {
  const text = sanitizeTerminalOutput(output);
  const modelUsageIndex = text.lastIndexOf('Model usage');
  if (modelUsageIndex < 0) return null;
  const section = text.slice(modelUsageIndex);
  const windows = [];
  const seenLabels = new Set();

  for (const rawLine of section.split(/\r?\n/).slice(1, 24)) {
    const line = rawLine.trimEnd();
    const percentageMatch = line.match(/(\d{1,3}(?:\.\d+)?)%/);
    if (!percentageMatch || percentageMatch.index == null) continue;
    const usedPercent = Number(percentageMatch[1]);
    if (!Number.isFinite(usedPercent) || usedPercent < 0 || usedPercent > 100) continue;

    const prefix = line.slice(0, percentageMatch.index).trimEnd();
    const label = prefix
      .split(/\s{2,}/, 1)[0]
      ?.trim();
    if (!isSafeLabel(label) || seenLabels.has(label.toLowerCase())) continue;

    const resetText = line.slice(percentageMatch.index + percentageMatch[0].length);
    const resetsAtEpochSeconds = parseRelativeReset(resetText, now);
    windows.push({
      label,
      usedFraction: Math.round((usedPercent / 100) * 10_000) / 10_000,
      ...(resetsAtEpochSeconds == null ? {} : { resetsAtEpochSeconds })
    });
    seenLabels.add(label.toLowerCase());
    if (windows.length >= MAX_WINDOWS) break;
  }

  if (windows.length === 0) return null;
  const tier = parseTier(text);
  return { windows, ...(tier == null ? {} : { tier }) };
}

function parseRelativeReset(value, now) {
  const match = value.match(/\((?:(\d+)h)?(?:\s*(\d+)m)?\)/i);
  if (!match) return null;
  const hours = Number(match[1] ?? 0);
  const minutes = Number(match[2] ?? 0);
  const totalMinutes = hours * 60 + minutes;
  if (!Number.isSafeInteger(totalMinutes) || totalMinutes <= 0 || totalMinutes > 31 * 24 * 60) {
    return null;
  }
  return Math.floor(now.getTime() / 1000) + totalMinutes * 60;
}

function parseTier(text) {
  const matches = [...text.matchAll(/^\s*Tier:\s*(.+?)\s*$/gim)];
  const tier = matches.at(-1)?.[1]?.trim();
  return isSafeText(tier, 64) ? tier : null;
}

function isSafeLabel(value) {
  return isSafeText(value, 32) && /^[\p{L}\p{N}][\p{L}\p{N} ._+-]*$/u.test(value);
}

function isSafeText(value, maxLength) {
  return typeof value === 'string' && value.length > 0 && value.length <= maxLength && !/[\u0000-\u001f\u007f]/.test(value);
}
