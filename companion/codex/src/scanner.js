import fs from 'node:fs';
import fsp from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import readline from 'node:readline';

const DAY_MILLIS = 24 * 60 * 60 * 1000;
const MAX_FILES = 20_000;
const MAX_FILE_BYTES = 64 * 1024 * 1024;
const MAX_TOTAL_BYTES = 512 * 1024 * 1024;
const MAX_LINE_BYTES = 1024 * 1024;
const MAX_EVENT_TOKENS = 20_000_000;
const MAX_MODEL_LABEL_LENGTH = 80;

export async function collectCodexTelemetry({
  codexHome = process.env.CODEX_HOME || path.join(os.homedir(), '.codex'),
  now = new Date(),
  maxDays = 30
} = {}) {
  if (!(now instanceof Date) || Number.isNaN(now.getTime())) throw new Error('Invalid current time');
  if (!Number.isInteger(maxDays) || maxDays < 1 || maxDays > 90) throw new Error('Invalid history range');

  const roots = [path.join(codexHome, 'sessions'), path.join(codexHome, 'archived_sessions')];
  const cutoffMillis = startOfLocalDay(new Date(now.getTime() - (maxDays - 1) * DAY_MILLIS)).getTime();
  const files = [];
  for (const root of roots) {
    await collectJsonlFiles(root, files);
    if (files.length >= MAX_FILES) break;
  }

  files.sort((left, right) => left.mtimeMs - right.mtimeMs || left.file.localeCompare(right.file, 'en'));
  const aggregate = {
    daily: new Map(),
    models: new Map(),
    currentContext: null,
    signatures: new Set(),
    remainingBytes: MAX_TOTAL_BYTES
  };
  for (const entry of files) {
    if (aggregate.remainingBytes <= 0) break;
    await scanSessionFile(entry, aggregate, cutoffMillis);
  }

  const daily = [...aggregate.daily.entries()]
    .sort(([left], [right]) => left.localeCompare(right, 'en'))
    .map(([date, totals]) => ({ date, ...totals }));
  const models = collapseModels(aggregate.models);
  const todayKey = localDateKey(now);
  const last7Cutoff = startOfLocalDay(new Date(now.getTime() - 6 * DAY_MILLIS)).getTime();

  return {
    schemaVersion: 1,
    source: 'codex-cli-local-jsonl',
    generatedAtEpochSeconds: Math.floor(now.getTime() / 1000),
    ...(aggregate.currentContext == null ? {} : { currentContext: aggregate.currentContext }),
    tokenUsage: {
      today: sumDaily(daily.filter((entry) => entry.date === todayKey)),
      last7Days: sumDaily(daily.filter((entry) => localDateMillis(entry.date) >= last7Cutoff)),
      last30Days: sumDaily(daily),
      daily,
      models
    }
  };
}

async function collectJsonlFiles(root, output) {
  let rootStat;
  try {
    rootStat = await fsp.lstat(root);
  } catch (error) {
    if (error?.code === 'ENOENT') return;
    throw error;
  }
  if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) return;

  const pending = [root];
  while (pending.length > 0 && output.length < MAX_FILES) {
    const directory = pending.pop();
    const entries = await fsp.readdir(directory, { withFileTypes: true });
    for (const entry of entries) {
      if (output.length >= MAX_FILES || entry.isSymbolicLink()) break;
      const candidate = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        pending.push(candidate);
      } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.jsonl')) {
        const stat = await fsp.stat(candidate);
        if (stat.size > 0 && stat.size <= MAX_FILE_BYTES) {
          output.push({ file: candidate, size: stat.size, mtimeMs: stat.mtimeMs });
        }
      }
    }
  }
}

async function scanSessionFile(entry, aggregate, cutoffMillis) {
  const byteBudget = Math.min(entry.size, aggregate.remainingBytes);
  aggregate.remainingBytes -= byteBudget;
  let consumedBytes = 0;
  let currentModel = null;
  let previousTotal = null;
  const input = fs.createReadStream(entry.file, { encoding: 'utf8', highWaterMark: 64 * 1024 });
  const lines = readline.createInterface({ input, crlfDelay: Infinity });

  try {
    for await (const line of lines) {
      consumedBytes += Buffer.byteLength(line, 'utf8') + 1;
      if (consumedBytes > byteBudget) break;
      if (Buffer.byteLength(line, 'utf8') > MAX_LINE_BYTES) continue;
      if (!line.includes('token_count') && !line.includes('turn_context')) continue;

      let record;
      try {
        record = JSON.parse(line);
      } catch {
        continue;
      }
      if (record?.type === 'turn_context') {
        currentModel = sanitizeModel(record?.payload?.model) ?? currentModel;
        continue;
      }
      const payload = record?.type === 'event_msg' ? record.payload : null;
      if (payload?.type !== 'token_count' || payload.info == null) continue;
      const capturedAt = parseTimestamp(record.timestamp);
      if (capturedAt == null) continue;

      const info = payload.info;
      const total = parseTotals(info.total_token_usage);
      const last = parseTotals(info.last_token_usage);
      const model = sanitizeModel(info.model ?? info.model_name ?? payload.model) ?? currentModel ?? 'Unknown';
      currentModel = model;

      const contextWindowTokens = safeInteger(info.model_context_window ?? payload.model_context_window);
      const contextTokens = last?.totalTokens ?? null;
      if (contextWindowTokens != null && contextWindowTokens > 0 && contextTokens != null) {
        const candidate = {
          capturedAtEpochSeconds: Math.floor(capturedAt.getTime() / 1000),
          model,
          usedTokens: contextTokens,
          contextWindowTokens,
          sessionTokens: total?.totalTokens ?? contextTokens
        };
        if (
          aggregate.currentContext == null ||
          candidate.capturedAtEpochSeconds >= aggregate.currentContext.capturedAtEpochSeconds
        ) {
          aggregate.currentContext = candidate;
        }
      }

      const delta = total == null
        ? last
        : previousTotal == null
          ? last ?? total
          : positiveDelta(total, previousTotal) ?? last;
      if (total != null) previousTotal = total;
      if (delta == null || delta.totalTokens <= 0 || delta.totalTokens > MAX_EVENT_TOKENS) continue;
      if (capturedAt.getTime() < cutoffMillis) continue;

      const signature = [
        record.timestamp,
        model,
        delta.inputTokens,
        delta.cachedInputTokens,
        delta.outputTokens,
        delta.reasoningOutputTokens
      ].join('|');
      if (aggregate.signatures.has(signature)) continue;
      if (aggregate.signatures.size < 250_000) aggregate.signatures.add(signature);

      addTotals(aggregate.daily, localDateKey(capturedAt), delta);
      addTotals(aggregate.models, model, delta);
    }
  } finally {
    lines.close();
    input.destroy();
  }
}

function parseTotals(value) {
  if (value == null || typeof value !== 'object' || Array.isArray(value)) return null;
  const inputTokens = safeInteger(value.input_tokens) ?? 0;
  const cachedInputTokens = safeInteger(value.cached_input_tokens ?? value.cache_read_input_tokens) ?? 0;
  const outputTokens = safeInteger(value.output_tokens) ?? 0;
  const reasoningOutputTokens = Math.min(
    safeInteger(value.reasoning_output_tokens) ?? 0,
    outputTokens
  );
  const explicitTotal = safeInteger(value.total_tokens);
  const totalTokens = explicitTotal ?? inputTokens + outputTokens;
  if (totalTokens < 0 || cachedInputTokens > inputTokens) return null;
  return { inputTokens, cachedInputTokens, outputTokens, reasoningOutputTokens, totalTokens };
}

function positiveDelta(current, previous) {
  const delta = {
    inputTokens: current.inputTokens - previous.inputTokens,
    cachedInputTokens: current.cachedInputTokens - previous.cachedInputTokens,
    outputTokens: current.outputTokens - previous.outputTokens,
    reasoningOutputTokens: current.reasoningOutputTokens - previous.reasoningOutputTokens,
    totalTokens: current.totalTokens - previous.totalTokens
  };
  if (Object.values(delta).some((value) => value < 0)) return null;
  return delta;
}

function addTotals(map, key, totals) {
  const existing = map.get(key) ?? emptyTotals();
  map.set(key, mergeTotals(existing, totals));
}

function mergeTotals(left, right) {
  return {
    inputTokens: left.inputTokens + right.inputTokens,
    cachedInputTokens: left.cachedInputTokens + right.cachedInputTokens,
    outputTokens: left.outputTokens + right.outputTokens,
    reasoningOutputTokens: left.reasoningOutputTokens + right.reasoningOutputTokens,
    totalTokens: left.totalTokens + right.totalTokens
  };
}

function sumDaily(entries) {
  return entries.reduce((sum, entry) => mergeTotals(sum, entry), emptyTotals());
}

function emptyTotals() {
  return {
    inputTokens: 0,
    cachedInputTokens: 0,
    outputTokens: 0,
    reasoningOutputTokens: 0,
    totalTokens: 0
  };
}

function collapseModels(models) {
  const ordered = [...models.entries()]
    .sort(([, left], [, right]) => right.totalTokens - left.totalTokens);
  const visible = ordered.slice(0, 8).map(([model, totals]) => ({ model, ...totals }));
  if (ordered.length > 8) {
    const other = ordered.slice(8).reduce((sum, [, totals]) => mergeTotals(sum, totals), emptyTotals());
    visible.push({ model: 'Other', ...other });
  }
  return visible;
}

function safeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function sanitizeModel(value) {
  if (typeof value !== 'string') return null;
  const cleaned = value.trim();
  if (cleaned.length === 0 || cleaned.length > MAX_MODEL_LABEL_LENGTH || /[\u0000-\u001f\u007f]/.test(cleaned)) {
    return null;
  }
  return cleaned;
}

function parseTimestamp(value) {
  if (typeof value !== 'string' || value.length > 64) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function startOfLocalDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function localDateKey(date) {
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function localDateMillis(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (match == null) return Number.NEGATIVE_INFINITY;
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])).getTime();
}
