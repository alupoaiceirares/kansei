package org.kansei.tailwind.controller;

import jakarta.validation.Valid;
import org.kansei.tailwind.dto.PhotoInfoResponse;
import org.kansei.tailwind.dto.SelectCommonsPhotoRequest;
import org.kansei.tailwind.photo.CommonsCandidate;
import org.kansei.tailwind.service.AircraftPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only management of an aircraft type's photo: search Wikimedia Commons, pick one, upload one, remove it.
 */
@RestController
@RequestMapping("/tailwind/admin/aircraft-types/{aircraftTypeId}/photo")
public class AircraftPhotoAdminController {

    private final AircraftPhotoService aircraftPhotoService;

    public AircraftPhotoAdminController(AircraftPhotoService aircraftPhotoService) {
        this.aircraftPhotoService = aircraftPhotoService;
    }

    @GetMapping("/candidates")
    public List<CommonsCandidate> candidates(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long aircraftTypeId,
            @RequestParam(required = false) String q
    ) {
        return aircraftPhotoService.candidates(userId, role, aircraftTypeId, q);
    }

    @PostMapping("/commons")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoInfoResponse selectFromCommons(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long aircraftTypeId,
            @Valid @RequestBody SelectCommonsPhotoRequest request
    ) {
        return aircraftPhotoService.selectFromCommons(userId, role, aircraftTypeId, request.title());
    }

    @PutMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoInfoResponse upload(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long aircraftTypeId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String license
    ) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file could not be read");
        }
        return aircraftPhotoService.upload(userId, role, aircraftTypeId, data, author, license);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long aircraftTypeId
    ) {
        aircraftPhotoService.delete(userId, role, aircraftTypeId);
    }
}
