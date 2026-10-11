// Keep only measured quotas. Account fields, prompts and OAuth credentials never
// enter a snapshot. Missing quota information is not equivalent to unused quota.
export function parseModelQuotas(response, nowEpochSeconds = Math.floor(Date.now() / 1000)) {
  const groups = response?.response?.groups ?? response?.groups;
  if (Array.isArray(groups)) return parseGroupedQuotas(groups, nowEpochSeconds);
  const status = response?.userStatus;
  const models = status?.cascadeModelConfigData?.clientModelConfigs ?? response?.clientModelConfigs;
  if (!Array.isArray(models)) throw new Error('No model quotas available');
  const labels = new Set();
  const windows = [];
  for (const model of models) {
    const label = model?.label;
    const remaining = model?.quotaInfo?.remainingFraction;
    if (typeof label !== 'string' || !label.trim() || label.length > 64 || /[\x00-\x1f\x7f]/.test(label)) continue;
    if (typeof remaining !== 'number' || !Number.isFinite(remaining) || remaining < 0 || remaining > 1) continue;
    if (labels.has(label.toLowerCase())) continue;
    const reset = Date.parse(model.quotaInfo.resetTime);
    const resetsAtEpochSeconds = Math.floor(reset / 1000);
    // An expired backend window must not be presented as a freshly measured quota.
    if (Number.isFinite(reset) && resetsAtEpochSeconds < nowEpochSeconds - 120) continue;
    labels.add(label.toLowerCase());
    windows.push({
      label,
      usedFraction: 1 - remaining,
      ...(Number.isFinite(reset) && resetsAtEpochSeconds <= nowEpochSeconds + 31 * 86400
        ? { resetsAtEpochSeconds } : {})
    });
  }
  if (windows.length === 0 || windows.length > 32) throw new Error('No supported model quotas available');
  return { windows };
}

function parseGroupedQuotas(groups, nowEpochSeconds) {
  const labels = new Map([
    ['gemini-weekly', 'Gemini models · Weekly'],
    ['gemini-5h', 'Gemini models · 5 hours'],
    ['3p-weekly', 'Claude/GPT models · Weekly'],
    ['3p-5h', 'Claude/GPT models · 5 hours']
  ]);
  const windows = [];
  const seen = new Set();
  for (const group of groups) {
    if (!Array.isArray(group?.buckets)) continue;
    for (const bucket of group.buckets) {
      const label = labels.get(bucket?.bucketId);
      const remaining = bucket?.remainingFraction;
      if (!label || seen.has(bucket.bucketId) || typeof remaining !== 'number' ||
          !Number.isFinite(remaining) || remaining < 0 || remaining > 1) continue;
      const reset = Date.parse(bucket.resetTime);
      const resetsAtEpochSeconds = Math.floor(reset / 1000);
      if (Number.isFinite(reset) && resetsAtEpochSeconds < nowEpochSeconds - 120) continue;
      seen.add(bucket.bucketId);
      windows.push({
        label,
        usedFraction: 1 - remaining,
        ...(Number.isFinite(reset) && resetsAtEpochSeconds <= nowEpochSeconds + 31 * 86400
          ? { resetsAtEpochSeconds } : {})
      });
    }
  }
  if (windows.length === 0) throw new Error('No supported grouped quotas available');
  return { windows };
}
