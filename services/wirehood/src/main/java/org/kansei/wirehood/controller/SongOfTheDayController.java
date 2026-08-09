package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.SongOfTheDayResponse;
import org.kansei.wirehood.service.SongOfTheDayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// Global/home-page data
@RestController
@RequestMapping("/wirehood/song-of-the-day")
public class SongOfTheDayController {

    private final SongOfTheDayService songOfTheDayService;

    public SongOfTheDayController(SongOfTheDayService songOfTheDayService) {
        this.songOfTheDayService = songOfTheDayService;
    }

    @GetMapping
    public Mono<SongOfTheDayResponse> today() {
        return songOfTheDayService.getToday();
    }
}
