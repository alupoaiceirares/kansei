import amqp from 'amqplib';

const MAIL_EXCHANGE = 'mail.events';
const QUEUE_NAME = 'courier-one.queue';
const ROUTING_KEYS = ['email.verification', 'email.password-reset'];

// Retry hop: a failed message parks here for RETRY_DELAY_MS, then TTL expiry dead-letters it back into MAIL_EXCHANGE (same routing key, so it lands on courier-one.queue again) for another attempt
const RETRY_EXCHANGE = 'mail.events.retry.dlx';
const RETRY_QUEUE = 'courier-one.queue.retry';
const RETRY_DELAY_MS = 30_000;
const MAX_RETRIES = 3;

// Give-up destination: after MAX_RETRIES, a message goes here instead of back to the retry queue - a fanout exchange so it's caught regardless of the original routing key
const DEAD_LETTER_EXCHANGE = 'mail.events.dlx';
const DEAD_LETTER_QUEUE = 'courier-one.queue.dlq';

export async function consumeMailEvents(handler) {
  const url = `amqp://${process.env.RABBITMQ_USERNAME}:${process.env.RABBITMQ_PASSWORD}@${process.env.RABBITMQ_HOST}:${process.env.RABBITMQ_PORT}`;
  const connection = await amqp.connect(url);
  const channel = await connection.createChannel();

  // Final DLQ - declared first since the main queue's failure path ends here after retries
  await channel.assertExchange(DEAD_LETTER_EXCHANGE, 'fanout', { durable: true });
  await channel.assertQueue(DEAD_LETTER_QUEUE, { durable: true });
  await channel.bindQueue(DEAD_LETTER_QUEUE, DEAD_LETTER_EXCHANGE, '');

  // Retry queue - nothing ever consumes this directly; it's a parking lot that expires messages back into the main exchange after RETRY_DELAY_MS
  await channel.assertExchange(RETRY_EXCHANGE, 'fanout', { durable: true });
  await channel.assertQueue(RETRY_QUEUE, {
    durable: true,
    arguments: {
      'x-message-ttl': RETRY_DELAY_MS,
      'x-dead-letter-exchange': MAIL_EXCHANGE,
    },
  });
  await channel.bindQueue(RETRY_QUEUE, RETRY_EXCHANGE, '');

  // Declared on both sides (the services declare the same exchange when publishing) so whichever service starts first doesn't fail
  await channel.assertExchange(MAIL_EXCHANGE, 'topic', { durable: true });
  const { queue } = await channel.assertQueue(QUEUE_NAME, {
    durable: true,
    arguments: { 'x-dead-letter-exchange': RETRY_EXCHANGE },
  });

  for (const routingKey of ROUTING_KEYS) {
    await channel.bindQueue(queue, MAIL_EXCHANGE, routingKey);
  }

  channel.consume(queue, async (msg) => {
    if (!msg) return;
    try {
      const payload = JSON.parse(msg.content.toString());
      await handler(payload);
      channel.ack(msg);
    } catch (err) {
      const attempt = retryCount(msg) + 1;
      if (attempt > MAX_RETRIES) {
        console.error(`Failed to process mail event after ${MAX_RETRIES} retries - giving up`, err);
        // nack would send it back through the queue's own x-dead-letter-exchange (the retry queue) again - publish straight to the final DLQ instead, then ack to remove it from the main queue without re-triggering that path
        channel.publish(DEAD_LETTER_EXCHANGE, '', msg.content, { headers: msg.properties.headers, persistent: true });
        channel.ack(msg);
      } else {
        console.error(`Failed to process mail event (attempt ${attempt}/${MAX_RETRIES}), retrying in ${RETRY_DELAY_MS}ms`, err);
        // Not requeued on the same queue (would spin immediately) - goes to the retry queue instead, via x-dead-letter-exchange, and comes back after RETRY_DELAY_MS
        channel.nack(msg, false, false);
      }
    }
  });

  console.log(`Listening on exchange "${MAIL_EXCHANGE}", queue "${queue}"`);
  return connection;
}

// RabbitMQ merges repeat dead-letterings of the same message from the same queue+reason into one x-death entry with an incrementing count, this reflects the true number of failed attempts regardless of how many retry hops happened
export function retryCount(msg) {
  const deaths = msg.properties.headers?.['x-death'];
  if (!Array.isArray(deaths)) return 0;
  const entry = deaths.find((d) => d.queue === QUEUE_NAME && d.reason === 'rejected');
  return entry ? entry.count : 0;
}
