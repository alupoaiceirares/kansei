import 'dotenv/config';
import { consumeMailEvents } from './rabbitmq.js';
import { render } from './templates.js';
import { sendMail } from './smtp.js';

async function handleMailEvent({ to, template, vars }) {
  const { subject, html } = render(template, vars);
  await sendMail({ to, subject, html });
  console.log(`Sent "${template}" to ${to}`);
}

consumeMailEvents(handleMailEvent).catch((err) => {
  console.error('Fatal error starting courier-one', err);
  process.exit(1);
});
