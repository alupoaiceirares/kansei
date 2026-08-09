package org.kansei.wirehood.scheduler;

import org.kansei.wirehood.service.SongOfTheDayService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SongOfTheDayScheduler {

    private final SongOfTheDayService songOfTheDayService;

    public SongOfTheDayScheduler(SongOfTheDayService songOfTheDayService) {
        this.songOfTheDayService = songOfTheDayService;
    }

    @Scheduled(cron = "${song-of-the-day.cron:0 0 0 * * *}")
    public void pickSongOfTheDay() {
        songOfTheDayService.pickForToday().block();
    }
}
