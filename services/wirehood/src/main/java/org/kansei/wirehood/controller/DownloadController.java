package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.SubmitDownloadRequest;
import org.kansei.wirehood.dto.SubmitDownloadResponse;
import org.kansei.wirehood.service.DownloadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/wirehood/downloads")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping
    public Mono<SubmitDownloadResponse> submit(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody SubmitDownloadRequest request
    ) {
        return downloadService.submit(userId, request);
    }
}
