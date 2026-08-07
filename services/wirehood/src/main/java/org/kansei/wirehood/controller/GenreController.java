package org.kansei.wirehood.controller;

import org.kansei.wirehood.model.Genre;
import org.kansei.wirehood.repository.GenreRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/wirehood/genres")
public class GenreController {

    private final GenreRepository genreRepository;

    public GenreController(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    // Fixed, seeded list - lets the frontend render tagging options without hardcoding them
    @GetMapping
    public Flux<Genre> list() {
        return genreRepository.findAll();
    }
}
