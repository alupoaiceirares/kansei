import fs from 'node:fs';
import path from 'node:path';

const RETENTION_DAYS = 14;
const DAY_MS = 24 * 60 * 60 * 1000;

function logFilePath(dir) {
  const date = new Date().toISOString().slice(0, 10);
  return path.join(dir, `courier-one-${date}.json`);
}

// Nested shape, not flat dotted keys - matches how the Java services' real ECS structured
// logging nests these same fields (service.name, log.level), so Alloy's extraction pipeline
// (infra/alloy/config.alloy) can use one JMESPath expression for every service's log file
function writeLine(level, message, err) {
  const dir = process.env.LOG_DIR;
  if (!dir) return;
  const line = {
    '@timestamp': new Date().toISOString(),
    log: { level },
    message,
    service: { name: 'courier-one' },
  };
  if (err) {
    line.error = { type: err.name, message: err.message, stack_trace: err.stack };
  }
  fs.mkdirSync(dir, { recursive: true });
  fs.appendFileSync(logFilePath(dir), JSON.stringify(line) + '\n');
}

export function info(message) {
  writeLine('INFO', message);
  console.log(message);
}

export function error(message, err) {
  writeLine('ERROR', message, err);
  console.error(message, err ?? '');
}

// Called on startup and once a day - deletes rolled-over log files past RETENTION_DAYS, same policy as the Java services' rollingpolicy.max-history
export function cleanOldLogs() {
  const dir = process.env.LOG_DIR;
  if (!dir || !fs.existsSync(dir)) return;
  const cutoff = Date.now() - RETENTION_DAYS * DAY_MS;
  for (const file of fs.readdirSync(dir)) {
    const filePath = path.join(dir, file);
    if (fs.statSync(filePath).mtimeMs < cutoff) {
      fs.unlinkSync(filePath);
    }
  }
}

export function startLogRetentionSchedule() {
  cleanOldLogs();
  setInterval(cleanOldLogs, DAY_MS).unref();
}
