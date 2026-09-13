package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.GenreProposalResponse;
import org.kansei.wirehood.dto.ProposeGenreRequest;
import org.kansei.wirehood.model.Genre;
import org.kansei.wirehood.repository.GenreRepository;
import org.kansei.wirehood.service.GenreProposalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/wirehood/genres")
public class GenreController {

    private final GenreRepository genreRepository;
    private final GenreProposalService genreProposalService;

    public GenreController(GenreRepository genreRepository, GenreProposalService genreProposalService) {
        this.genreRepository = genreRepository;
        this.genreProposalService = genreProposalService;
    }

    // Fixed, seeded list - lets the frontend render tagging options without hardcoding them
    @GetMapping
    public Flux<Genre> list() {
        return genreRepository.findAll();
    }

    // Any wirehood user - sits PENDING until an admin approves/rejects it, see AdminGenreProposalController
    @PostMapping("/proposals")
    public Mono<ResponseEntity<GenreProposalResponse>> propose(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ProposeGenreRequest request
    ) {
        return genreProposalService.submit(userId, request.name())
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }
}
