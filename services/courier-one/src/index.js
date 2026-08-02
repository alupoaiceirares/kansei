import 'dotenv/config';
import { pathToFileURL } from 'node:url';
import { consumeMailEvents } from './rabbitmq.js';
import { render } from './templates.js';
import { sendMail } from './smtp.js';

export async function handleMailEvent({ to, template, vars }, deps = { render, sendMail }) {
  const { subject, html } = deps.render(template, vars);
  await deps.sendMail({ to, subject, html });
  console.log(`Sent "${template}" to ${to}`);
}

// Only start consuming when run directly (`node src/index.js`) - not when imported by tests.
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  consumeMailEvents(handleMailEvent).catch((err) => {
    console.error('Fatal error starting courier-one', err);
    process.exit(1);
  });
}
