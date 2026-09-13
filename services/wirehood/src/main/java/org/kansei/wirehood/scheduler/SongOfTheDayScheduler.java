package org.kansei.wirehood.scheduler;

import org.kansei.wirehood.service.SongOfTheDayService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SongOfTheDayScheduler {

    private static final Logger log = LoggerFactory.getLogger(SongOfTheDayScheduler.class);

    private final SongOfTheDayService songOfTheDayService;

    public SongOfTheDayScheduler(SongOfTheDayService songOfTheDayService) {
        this.songOfTheDayService = songOfTheDayService;
    }

    @Scheduled(cron = "${song-of-the-day.cron:0 0 0 * * *}")
    public void pickSongOfTheDay() {
        songOfTheDayService.pickForToday().block();
    }

    // Self-heals the case where the service was down at midnight and the cron above never fired
    // for today - upsert() is ON CONFLICT DO NOTHING, so this is a harmless no-op on every normal
    // startup where today's pick already exists, not a re-roll.
    // Caught explicitly, not left to propagate: an ApplicationReadyEvent listener that throws
    // aborts the whole app's startup (unlike @Scheduled, whose exceptions Spring just logs) - a
    // transient DB hiccup here should degrade to "no song of the day today", never "wirehood won't start".
    @EventListener(ApplicationReadyEvent.class)
    public void pickOnStartupIfMissing() {
        try {
            songOfTheDayService.pickForToday().block();
        } catch (Exception ex) {
            log.error("Song of the day startup pick failed, will retry at the next scheduled run", ex);
        }
    }
}
