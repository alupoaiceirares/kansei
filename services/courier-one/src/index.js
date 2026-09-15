import 'dotenv/config';
import { pathToFileURL } from 'node:url';
import { consumeMailEvents } from './rabbitmq.js';
import { render } from './templates.js';
import { sendMail } from './smtp.js';
import { startHealthServer, markReady } from './health.js';
import { info, error, startLogRetentionSchedule } from './logger.js';

export async function handleMailEvent({ to, template, vars }, deps = { render, sendMail }) {
  const { subject, html } = deps.render(template, vars);
  await deps.sendMail({ to, subject, html });
  info(`Sent "${template}" to ${to}`);
}

// Only start consuming when run directly (`node src/index.js`) - not when imported by tests
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  startLogRetentionSchedule();
  startHealthServer(process.env.HEALTH_PORT);
  consumeMailEvents(handleMailEvent)
    .then(() => markReady())
    .catch((err) => {
      error('Fatal error starting courier-one', err);
      process.exit(1);
    });
}
