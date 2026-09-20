package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.dto.PhotoInfoResponse;
import org.kansei.tailwind.model.AircraftPhoto;
import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.PhotoOrigin;
import org.kansei.tailwind.photo.CommonsCandidate;
import org.kansei.tailwind.photo.CommonsClient;
import org.kansei.tailwind.photo.CommonsUnavailableException;
import org.kansei.tailwind.photo.ImageDownloader;
import org.kansei.tailwind.photo.ImageMagicBytes;
import org.kansei.tailwind.photo.PhotoStorage;
import org.kansei.tailwind.repository.AircraftPhotoRepository;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One representative photo per aircraft type. Anyone can read it, only admins change it, and every change
 * is audited. A type without a photo serves the placeholder, so the frontend never has to handle a missing image.
 */
@Slf4j
@Service
public class AircraftPhotoService {

    public record PhotoContent(byte[] bytes, String contentType, boolean placeholder) {
    }

    private static final int CANDIDATE_LIMIT = 12;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_TEXT_LENGTH = 300;

    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftPhotoRepository aircraftPhotoRepository;
    private final PhotoStorage photoStorage;
    private final CommonsClient commonsClient;
    private final ImageDownloader imageDownloader;
    private final AdminAuthService adminAuthService;
    private final AuditPublisher auditPublisher;
    private final Clock clock;
    private final int maxBytes;
    private volatile byte[] placeholder;

    public AircraftPhotoService(AircraftTypeRepository aircraftTypeRepository, AircraftPhotoRepository aircraftPhotoRepository,
                                PhotoStorage photoStorage, CommonsClient commonsClient, ImageDownloader imageDownloader,
                                AdminAuthService adminAuthService, AuditPublisher auditPublisher, Clock clock,
                                @Value("${tailwind.photos.max-bytes}") int maxBytes) {
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.aircraftPhotoRepository = aircraftPhotoRepository;
        this.photoStorage = photoStorage;
        this.commonsClient = commonsClient;
        this.imageDownloader = imageDownloader;
        this.adminAuthService = adminAuthService;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
        this.maxBytes = maxBytes;
    }

    // ---- reading, open to everyone

    public PhotoContent photoForType(Long aircraftTypeId) {
        requireType(aircraftTypeId);
        return contentOf(aircraftPhotoRepository.findByAircraftTypeId(aircraftTypeId));
    }

    public PhotoContent photoForFamily(String family) {
        requireFamily(family);
        return contentOf(aircraftPhotoRepository.findByFamily(family).stream().findFirst());
    }

    public PhotoInfoResponse infoForType(Long aircraftTypeId) {
        requireType(aircraftTypeId);
        return aircraftPhotoRepository.findByAircraftTypeId(aircraftTypeId).map(PhotoInfoResponse::of).orElse(PhotoInfoResponse.none());
    }

    public PhotoInfoResponse infoForFamily(String family) {
        requireFamily(family);
        return aircraftPhotoRepository.findByFamily(family).stream().findFirst().map(PhotoInfoResponse::of).orElse(PhotoInfoResponse.none());
    }

    // ---- admin

    public List<CommonsCandidate> candidates(UUID adminId, String role, Long aircraftTypeId, String query) {
        adminAuthService.requireAdmin(adminId, role);
        AircraftType type = requireType(aircraftTypeId);
        String searchText = query != null && !query.isBlank() ? query.trim() : type.getManufacturer() + " " + type.getModel() + " aircraft";
        if (searchText.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is too long");
        }
        try {
            return commonsClient.search(searchText, CANDIDATE_LIMIT);
        } catch (CommonsUnavailableException ex) {
            log.warn("Commons search failed: {}", ex.getMessage());
            throw commonsDown();
        }
    }

    // The title is the only thing trusted from the client, Commons is asked again for the URL, author and license
    public PhotoInfoResponse selectFromCommons(UUID adminId, String role, Long aircraftTypeId, String title) {
        adminAuthService.requireAdmin(adminId, role);
        requireType(aircraftTypeId);
        CommonsCandidate candidate;
        try {
            candidate = commonsClient.findByTitle(title).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That file is not a usable JPEG or PNG on Commons"));
        } catch (CommonsUnavailableException ex) {
            log.warn("Commons lookup failed: {}", ex.getMessage());
            throw commonsDown();
        }
        byte[] data;
        try {
            data = imageDownloader.download(candidate.thumbUrl(), maxBytes);
        } catch (ImageDownloader.ImageDownloadException ex) {
            log.warn("Commons image download failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The photo could not be downloaded: " + ex.getMessage());
        }
        AircraftPhoto saved = store(aircraftTypeId, data, PhotoOrigin.COMMONS, candidate.title(), candidate.author(), candidate.license(), candidate.pageUrl());
        auditPublisher.publishWithResolvedUsername("SET_AIRCRAFT_PHOTO", adminId, "AIRCRAFT_TYPE", String.valueOf(aircraftTypeId),
                Map.of("origin", "COMMONS", "title", candidate.title()));
        return PhotoInfoResponse.of(saved);
    }

    public PhotoInfoResponse upload(UUID adminId, String role, Long aircraftTypeId, byte[] data, String author, String license) {
        adminAuthService.requireAdmin(adminId, role);
        requireType(aircraftTypeId);
        if (data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        if (data.length > maxBytes) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "The photo is larger than " + maxBytes + " bytes");
        }
        AircraftPhoto saved = store(aircraftTypeId, data, PhotoOrigin.UPLOAD, null, cleanText(author), cleanText(license), null);
        auditPublisher.publishWithResolvedUsername("UPLOAD_AIRCRAFT_PHOTO", adminId, "AIRCRAFT_TYPE", String.valueOf(aircraftTypeId), Map.of("origin", "UPLOAD"));
        return PhotoInfoResponse.of(saved);
    }

    public void delete(UUID adminId, String role, Long aircraftTypeId) {
        adminAuthService.requireAdmin(adminId, role);
        requireType(aircraftTypeId);
        AircraftPhoto photo = aircraftPhotoRepository.findByAircraftTypeId(aircraftTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This aircraft type has no photo"));
        aircraftPhotoRepository.delete(photo);
        photoStorage.delete(photo.getFileName());
        auditPublisher.publishWithResolvedUsername("DELETE_AIRCRAFT_PHOTO", adminId, "AIRCRAFT_TYPE", String.valueOf(aircraftTypeId), Map.of());
    }

    // ---- internals

    // Checks what the bytes really are, writes the file, then points the row at it and drops the replaced file
    private AircraftPhoto store(Long aircraftTypeId, byte[] data, PhotoOrigin origin, String title, String author, String license, String sourceUrl) {
        String extension = ImageMagicBytes.detectExtension(data)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is not a JPEG or PNG image"));
        String fileName;
        try {
            fileName = photoStorage.write(data, extension);
        } catch (UncheckedIOException ex) {
            log.error("could not write the aircraft photo", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The photo could not be stored");
        }

        Optional<AircraftPhoto> previous = aircraftPhotoRepository.findByAircraftTypeId(aircraftTypeId);
        String previousFile = previous.map(AircraftPhoto::getFileName).orElse(null);
        AircraftPhoto photo = previous.orElseGet(AircraftPhoto::new);
        photo.setAircraftTypeId(aircraftTypeId);
        photo.setFileName(fileName);
        photo.setContentType(ImageMagicBytes.contentTypeFor(extension));
        photo.setOrigin(origin);
        photo.setTitle(title);
        photo.setAuthor(author);
        photo.setLicense(license);
        photo.setSourceUrl(sourceUrl);
        photo.setCreatedAt(clock.instant());
        AircraftPhoto saved;
        try {
            saved = aircraftPhotoRepository.saveAndFlush(photo);
        } catch (RuntimeException ex) {
            photoStorage.delete(fileName);
            throw ex;
        }
        if (previousFile != null) {
            photoStorage.delete(previousFile);
        }
        return saved;
    }

    private PhotoContent contentOf(Optional<AircraftPhoto> photo) {
        if (photo.isPresent()) {
            Optional<byte[]> bytes = photoStorage.read(photo.get().getFileName());
            if (bytes.isPresent()) {
                return new PhotoContent(bytes.get(), photo.get().getContentType(), false);
            }
            log.warn("photo file {} is missing from storage, serving the placeholder", photo.get().getFileName());
        }
        return new PhotoContent(placeholderBytes(), "image/svg+xml", true);
    }

    private byte[] placeholderBytes() {
        byte[] loaded = placeholder;
        if (loaded == null) {
            try {
                loaded = new ClassPathResource("photos/placeholder.svg").getInputStream().readAllBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            placeholder = loaded;
        }
        return loaded;
    }

    private AircraftType requireType(Long aircraftTypeId) {
        return aircraftTypeRepository.findById(aircraftTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aircraft type not found"));
    }

    private void requireFamily(String family) {
        if (family == null || family.isBlank() || !aircraftTypeRepository.existsByFamily(family)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aircraft family not found");
        }
    }

    private static String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    private static ResponseStatusException commonsDown() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wikimedia Commons is not answering, try again later or upload a photo");
    }
}
