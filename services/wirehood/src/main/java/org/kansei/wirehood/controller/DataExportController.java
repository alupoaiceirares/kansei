package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.DataExportResponse;
import org.kansei.wirehood.service.DataExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/wirehood/export")
public class DataExportController {

    private final DataExportService dataExportService;

    public DataExportController(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }

    // Content-Disposition makes hitting this directly in a browser trigger an actual file download, not just render the JSON
    @GetMapping
    public Mono<ResponseEntity<DataExportResponse>> export(@RequestHeader("X-User-Id") UUID userId) {
        return dataExportService.export(userId)
                .map(body -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wirehood-export.json\"")
                        .body(body));
    }
}
