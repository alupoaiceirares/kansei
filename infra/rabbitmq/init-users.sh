#!/bin/sh
# One-shot: creates one RabbitMQ user per producing/consuming service instead of every service
# sharing RABBITMQ_USER, and scopes each to only the exchanges/queues it actually touches (regex
# permissions). PUT is idempotent, so re-running on an existing vhost is safe. RABBITMQ_USER stays
# the broker's own admin login (management UI + this script), no service authenticates as it.
set -eu

BASE="http://rabbitmq:15672/api"
AUTH="${RABBITMQ_USER}:${RABBITMQ_PASSWORD}"
VHOST="%2F"

wait_for_management() {
  i=0
  until curl -sf -u "$AUTH" "$BASE/overview" >/dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -ge 30 ]; then
      echo "RabbitMQ management API never became ready" >&2
      exit 1
    fi
    sleep 2
  done
}

# args: username password configure_regex write_regex read_regex
create_user() {
  name="$1"
  pass="$2"
  configure="$3"
  write="$4"
  read_re="$5"

  curl -sf -u "$AUTH" -X PUT "$BASE/users/$name" \
    -H "content-type: application/json" \
    -d "{\"password\":\"$pass\",\"tags\":\"\"}"

  curl -sf -u "$AUTH" -X PUT "$BASE/permissions/$VHOST/$name" \
    -H "content-type: application/json" \
    -d "{\"configure\":\"$configure\",\"write\":\"$write\",\"read\":\"$read_re\"}"

  echo "provisioned RabbitMQ user: $name"
}

wait_for_management

# Single-quoted: these need a literal double backslash before each dot to survive both the shell
# (unchanged in single quotes) and JSON encoding (\\ decodes to one literal backslash), so RabbitMQ
# sees a regex with an actually-escaped dot, not "any character"
# shieldwall: publishes mail.events + audit.events, never reads a queue
create_user "shieldwall" "$SHIELDWALL_RABBITMQ_PASSWORD" \
  '^(mail\\.events|audit\\.events)$' \
  '^(mail\\.events|audit\\.events)$' \
  '^$'

# wirehood: owns wirehood.download-jobs (produce+consume), publishes mail.events + audit.events
create_user "wirehood" "$WIREHOOD_RABBITMQ_PASSWORD" \
  '^(wirehood\\.download-jobs|mail\\.events|audit\\.events)$' \
  '^(wirehood\\.download-jobs|mail\\.events|audit\\.events)$' \
  '^(wirehood\\.download-jobs)$'

# courier-one: owns its mail.events consumer chain (main/retry/dlq queues + retry/dlx exchanges).
# Binding a queue needs read on the source exchange too, and a queue's dead-letter-exchange
# argument needs write on the target, not just the queues themselves.
create_user "courier-one" "$COURIER_ONE_RABBITMQ_PASSWORD" \
  '^(mail\\.events|mail\\.events\\.retry\\.dlx|mail\\.events\\.dlx|courier-one\\.queue|courier-one\\.queue\\.retry|courier-one\\.queue\\.dlq)$' \
  '^(mail\\.events|mail\\.events\\.retry\\.dlx|mail\\.events\\.dlx|courier-one\\.queue|courier-one\\.queue\\.retry|courier-one\\.queue\\.dlq)$' \
  '^(mail\\.events|mail\\.events\\.retry\\.dlx|mail\\.events\\.dlx|courier-one\\.queue|courier-one\\.queue\\.retry|courier-one\\.queue\\.dlq)$'

# fdr: sole consumer of fdr.audit, owns its dlx/dlq, never publishes a real message itself.
# queue.bind needs write on the queue plus read on the source exchange, so both queues land in
# write_re too even though fdr never calls basic.publish on them.
create_user "fdr" "$FDR_RABBITMQ_PASSWORD" \
  '^(audit\\.events|fdr\\.audit|fdr\\.audit\\.dlx|fdr\\.audit\\.dlq)$' \
  '^(fdr\\.audit|fdr\\.audit\\.dlx|fdr\\.audit\\.dlq)$' \
  '^(audit\\.events|fdr\\.audit|fdr\\.audit\\.dlx|fdr\\.audit\\.dlq)$'

# tailwind: publishes audit.events (admin actions) and later mail.events (disable requests, yearly recap), never reads a queue
create_user "tailwind" "$TAILWIND_RABBITMQ_PASSWORD" \
  '^(mail\\.events|audit\\.events)$' \
  '^(mail\\.events|audit\\.events)$' \
  '^$'
