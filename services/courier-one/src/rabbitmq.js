import amqp from 'amqplib';

const MAIL_EXCHANGE = 'mail.events';
const QUEUE_NAME = 'courier-one.queue';
const ROUTING_KEYS = ['email.verification', 'email.password-reset'];

export async function consumeMailEvents(handler) {
  const url = `amqp://${process.env.RABBITMQ_USERNAME}:${process.env.RABBITMQ_PASSWORD}@${process.env.RABBITMQ_HOST}:${process.env.RABBITMQ_PORT}`;
  const connection = await amqp.connect(url);
  const channel = await connection.createChannel();

  // Declared on both sides (the services declare the same exchange when publishing) so whichever service starts first doesn't fail.
  await channel.assertExchange(MAIL_EXCHANGE, 'topic', { durable: true });
  const { queue } = await channel.assertQueue(QUEUE_NAME, { durable: true });

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
      console.error('Failed to process mail event', err);
      // Not requeued - a broken payload or permanent send failure would loop forever otherwise.
      channel.nack(msg, false, false);
    }
  });

  console.log(`Listening on exchange "${MAIL_EXCHANGE}", queue "${queue}"`);
  return connection;
}
