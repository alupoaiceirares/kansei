package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.CsvImportResponse;
import org.kansei.tailwind.dto.ImportPreviewResponse;
import org.kansei.tailwind.service.CsvImportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only CSV import for a user: a dry run, the commit (the same file sent again) and the run history.
 */
@RestController
@RequestMapping("/tailwind/admin/imports")
public class CsvImportController {

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(path = "/preview", consumes = "multipart/form-data")
    public ImportPreviewResponse preview(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam UUID targetUserId,
            @RequestPart("file") MultipartFile file
    ) {
        return csvImportService.preview(userId, role, targetUserId, file);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public CsvImportResponse commit(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam UUID targetUserId,
            @RequestPart("file") MultipartFile file
    ) {
        return csvImportService.commit(userId, role, targetUserId, file);
    }

    @GetMapping
    public List<CsvImportResponse> history(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return csvImportService.history(userId, role);
    }
}
