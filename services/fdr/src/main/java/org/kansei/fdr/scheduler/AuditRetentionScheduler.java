package org.kansei.fdr.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionScheduler.class);

    private final JdbcClient jdbcClient;
    private final int retentionDays;

    public AuditRetentionScheduler(JdbcClient jdbcClient, @Value("${fdr.audit.retention-days}") int retentionDays) {
        this.jdbcClient = jdbcClient;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${fdr.audit.retention-cron:0 0 3 * * *}")
    public void purgeExpiredAuditLog() {
        int deleted = jdbcClient.sql("DELETE FROM audit_log WHERE occurred_at < now() - make_interval(days => :days)")
                .param("days", retentionDays)
                .update();
        log.info("Purged {} audit_log rows older than {} days", deleted, retentionDays);
    }
}
