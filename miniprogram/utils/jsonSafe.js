/**
 * 在 JSON.parse 前将 16 位及以上的裸整数字面量改为字符串，避免微信小程序内 JSON.parse 使用
 * IEEE754 导致雪花 ID 等超大整数精度丢失。
 *
 * 处理：1) "key": 123...  2) 数组元素 [ ..., 123..., ... ]
 */
function parseJsonPreservingBigInts(raw) {
  if (raw == null || raw === '') return raw;
  if (typeof raw !== 'string') {
    return raw;
  }
  const trimmed = raw.trim();
  if (!trimmed) {
    return raw;
  }
  let fixed = trimmed.replace(
    /"([^"\\]+)"\s*:\s*(\d{16,})(?=\s*[,}\]])/g,
    '"$1":"$2"'
  );
  fixed = fixed.replace(
    /(\[|,)\s*(\d{16,})(?=\s*[,}\]])/g,
    '$1"$2"'
  );
  return JSON.parse(fixed);
}

/** 比较两个 ID（雪花 ID 等应已解析为字符串后再比较） */
function idsEqual(a, b) {
  if (a == null || b == null) return false;
  return String(a) === String(b);
}

module.exports = { parseJsonPreservingBigInts, idsEqual };
