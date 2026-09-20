package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.PhotoInfoResponse;
import org.kansei.tailwind.service.AircraftPhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Aircraft photos. The two image routes are open at the gateway (a plain img tag cannot send a token, the
 * photos are shared cosmetic images), so they never look at the caller. The attribution routes are authenticated.
 * A family goes in a query parameter because some family names contain a slash.
 */
@RestController
@RequestMapping("/tailwind")
public class AircraftPhotoController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final AircraftPhotoService aircraftPhotoService;

    public AircraftPhotoController(AircraftPhotoService aircraftPhotoService) {
        this.aircraftPhotoService = aircraftPhotoService;
    }

    @GetMapping("/aircraft-types/{aircraftTypeId}/photo")
    public ResponseEntity<byte[]> photoForType(@PathVariable Long aircraftTypeId) {
        return image(aircraftPhotoService.photoForType(aircraftTypeId));
    }

    @GetMapping("/aircraft-families/photo")
    public ResponseEntity<byte[]> photoForFamily(@RequestParam("name") String family) {
        return image(aircraftPhotoService.photoForFamily(family));
    }

    @GetMapping("/aircraft-types/{aircraftTypeId}/photo/info")
    public PhotoInfoResponse infoForType(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long aircraftTypeId) {
        return aircraftPhotoService.infoForType(aircraftTypeId);
    }

    @GetMapping("/aircraft-families/photo/info")
    public PhotoInfoResponse infoForFamily(@RequestHeader("X-User-Id") UUID userId, @RequestParam("name") String family) {
        return aircraftPhotoService.infoForFamily(family);
    }

    private static ResponseEntity<byte[]> image(AircraftPhotoService.PhotoContent content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CACHE)
                .header("X-Photo-Placeholder", String.valueOf(content.placeholder()))
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }
}
