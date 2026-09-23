package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.aircraft.AircraftResolver;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.dto.CsvImportResponse;
import org.kansei.tailwind.dto.ImportPreviewResponse;
import org.kansei.tailwind.dto.ImportPreviewResponse.Row;
import org.kansei.tailwind.dto.ImportPreviewResponse.Status;
import org.kansei.tailwind.dto.JourneyRequest;
import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.Airline;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.model.CabinClass;
import org.kansei.tailwind.model.CsvImport;
import org.kansei.tailwind.model.Flight;
import org.kansei.tailwind.model.SeatPosition;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.model.TripReason;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.reference.CsvReader;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.CsvImportRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin import of a user's past flights from a CSV file. The dry run and the commit share one analysis, so the
 * preview is exactly what gets written. No provider calls, rows land as manual flights with the user's default visibility.
 */
@Slf4j
@Service
public class CsvImportService {

    public static final Set<String> COLUMNS = new LinkedHashSet<>(List.of("date", "flight_number", "airline", "from", "to",
            "departure_time", "arrival_time", "aircraft", "seat", "seat_position", "cabin_class", "reason", "notes", "cargo", "journey", "visibility"));
    private static final List<String> REQUIRED = List.of("date", "from", "to");
    private static final Pattern FLIGHT_NUMBER = Pattern.compile("^[A-Z0-9]{2,3}\\d{1,4}[A-Z]?$");
    private static final int MAX_STORED_ERRORS = 200;
    private static final int HISTORY_SIZE = 50;

    private final AdminAuthService adminAuthService;
    private final TailwindUserRepository tailwindUserRepository;
    private final UserFlightRepository userFlightRepository;
    private final AirlineRepository airlineRepository;
    private final AirportRepository airportRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final AircraftResolver aircraftResolver;
    private final UserFlightService userFlightService;
    private final JourneyService journeyService;
    private final CsvImportRepository csvImportRepository;
    private final ShieldwallUserClient shieldwallUserClient;
    private final AuditPublisher auditPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final long maxBytes;
    private final int maxRows;

    public CsvImportService(AdminAuthService adminAuthService, TailwindUserRepository tailwindUserRepository, UserFlightRepository userFlightRepository,
                            AirlineRepository airlineRepository, AirportRepository airportRepository, AircraftTypeRepository aircraftTypeRepository,
                            AircraftResolver aircraftResolver, UserFlightService userFlightService, JourneyService journeyService,
                            CsvImportRepository csvImportRepository, ShieldwallUserClient shieldwallUserClient, AuditPublisher auditPublisher,
                            ObjectMapper objectMapper, Clock clock,
                            @Value("${tailwind.import.max-bytes}") long maxBytes,
                            @Value("${tailwind.import.max-rows}") int maxRows) {
        this.adminAuthService = adminAuthService;
        this.tailwindUserRepository = tailwindUserRepository;
        this.userFlightRepository = userFlightRepository;
        this.airlineRepository = airlineRepository;
        this.airportRepository = airportRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.aircraftResolver = aircraftResolver;
        this.userFlightService = userFlightService;
        this.journeyService = journeyService;
        this.csvImportRepository = csvImportRepository;
        this.shieldwallUserClient = shieldwallUserClient;
        this.auditPublisher = auditPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxBytes = maxBytes;
        this.maxRows = maxRows;
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(UUID adminId, String role, UUID targetUserId, MultipartFile file) {
        adminAuthService.requireAdmin(adminId, role);
        requireTarget(targetUserId);
        List<Analyzed> rows = analyze(targetUserId, read(file));
        return new ImportPreviewResponse(targetUserId, usernames(List.of(targetUserId)).get(targetUserId), rows.size(),
                count(rows, Status.READY), count(rows, Status.DUPLICATE), count(rows, Status.ERROR), rows.stream().map(Analyzed::view).toList());
    }

    // Writes the ready rows in one transaction, duplicates and bad rows are skipped and recorded
    @Transactional
    public CsvImportResponse commit(UUID adminId, String role, UUID targetUserId, MultipartFile file) {
        adminAuthService.requireAdmin(adminId, role);
        TailwindUser target = requireTarget(targetUserId);
        List<Analyzed> rows = analyze(targetUserId, read(file));

        // Rows sharing a journey label go into one titled journey, the rest get one automatic journey each
        Map<String, Long> journeys = new HashMap<>();
        int imported = 0;
        for (Analyzed row : rows) {
            if (row.flight() == null) {
                continue;
            }
            String label = row.flight().journey();
            Long journeyId = label == null ? null
                    : journeys.computeIfAbsent(label, l -> journeyService.create(targetUserId, new JourneyRequest(l, null)).id());
            userFlightService.addImported(targetUserId, row.flight(), journeyId, target.getDefaultVisibility());
            imported++;
        }

        List<CsvImportResponse.RowError> errors = rows.stream().filter(r -> r.view().status() == Status.ERROR)
                .limit(MAX_STORED_ERRORS).map(r -> new CsvImportResponse.RowError(r.view().line(), r.view().message())).toList();
        CsvImport run = csvImportRepository.save(CsvImport.builder()
                .adminUserId(adminId)
                .targetUserId(targetUserId)
                .fileName(fileName(file))
                .totalRows(rows.size())
                .importedRows(imported)
                .duplicateRows(count(rows, Status.DUPLICATE))
                .errorRows(count(rows, Status.ERROR))
                .errors(objectMapper.writeValueAsString(errors))
                .createdAt(clock.instant())
                .build());
        auditPublisher.publishWithResolvedUsername("IMPORT_FLIGHTS", adminId, "TAILWIND_USER", targetUserId.toString(),
                Map.of("importId", run.getId(), "importedRows", imported, "duplicateRows", run.getDuplicateRows(), "errorRows", run.getErrorRows()));
        return toResponse(run, usernames(List.of(adminId, targetUserId)));
    }

    @Transactional(readOnly = true)
    public List<CsvImportResponse> history(UUID adminId, String role) {
        adminAuthService.requireAdmin(adminId, role);
        List<CsvImport> runs = csvImportRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, HISTORY_SIZE));
        Set<UUID> ids = new HashSet<>();
        runs.forEach(run -> {
            ids.add(run.getAdminUserId());
            ids.add(run.getTargetUserId());
        });
        Map<UUID, String> names = usernames(ids);
        return runs.stream().map(run -> toResponse(run, names)).toList();
    }

    // ---- analysis

    private record Analyzed(Row view, ImportedFlight flight) {
    }

    // Same day and route, and the same flight number unless one side has none
    private record DuplicateKey(LocalDate date, Long from, Long to, String number) {
        boolean sameFlightAs(DuplicateKey other) {
            return date.equals(other.date) && from.equals(other.from) && to.equals(other.to)
                    && (number == null || other.number == null || number.equals(other.number));
        }
    }

    private List<Analyzed> analyze(UUID targetUserId, List<Map<String, String>> records) {
        LocalDate today = LocalDate.now(clock);
        List<DuplicateKey> taken = new ArrayList<>();
        for (UserFlight existing : userFlightRepository.findDetailedByUserId(targetUserId)) {
            Flight f = existing.getFlight();
            taken.add(new DuplicateKey(f.getFlightDate(), f.getDepartureAirport().getId(), f.getArrivalAirport().getId(), f.getFlightNumber()));
        }

        List<Analyzed> result = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            Map<String, String> record = records.get(i);
            int line = i + 2;
            if (record.values().stream().allMatch(String::isBlank)) {
                continue;
            }
            ImportedFlight flight;
            try {
                flight = resolve(record, today);
            } catch (RowException ex) {
                result.add(new Analyzed(view(line, Status.ERROR, ex.getMessage(), record, null), null));
                continue;
            }
            DuplicateKey key = new DuplicateKey(flight.date(), flight.departure().getId(), flight.arrival().getId(), flight.flightNumber());
            if (taken.stream().anyMatch(key::sameFlightAs)) {
                result.add(new Analyzed(view(line, Status.DUPLICATE, "Already in the log or earlier in this file", record, flight), null));
                continue;
            }
            taken.add(key);
            result.add(new Analyzed(view(line, Status.READY, null, record, flight), flight));
        }
        return result;
    }

    private ImportedFlight resolve(Map<String, String> r, LocalDate today) {
        LocalDate date = parseDate(value(r, "date"), today);
        String number = flightNumber(value(r, "flight_number"));
        Airline airline = airline(value(r, "airline"), number);
        Airport from = airport(value(r, "from"), "from");
        Airport to = airport(value(r, "to"), "to");
        if (from.getId().equals(to.getId())) {
            throw new RowException("from and to are the same airport");
        }

        // A designator (A320) first, then the resolver on free text. Unresolved text is kept and shows up as unmapped
        String aircraftText = value(r, "aircraft");
        AircraftType type = null;
        String family = null;
        if (aircraftText != null) {
            type = aircraftTypeRepository.findByIcaoCode(aircraftText.toUpperCase(Locale.ROOT)).orElse(null);
            if (type != null) {
                family = type.getFamily();
            } else {
                AircraftResolver.Resolution resolution = aircraftResolver.resolve(aircraftText, null);
                type = resolution.type();
                family = resolution.family();
            }
        }

        String seat = value(r, "seat");
        if (seat != null && seat.length() > 8) {
            throw new RowException("seat is longer than 8 characters");
        }
        String notes = value(r, "notes");
        if (notes != null && notes.length() > 2000) {
            throw new RowException("notes are longer than 2000 characters");
        }
        String journey = value(r, "journey");
        if (journey != null && journey.length() > 255) {
            throw new RowException("journey is longer than 255 characters");
        }
        return new ImportedFlight(date, number, airline, from, to, parseTime(value(r, "departure_time"), "departure_time"),
                parseTime(value(r, "arrival_time"), "arrival_time"), type, family, aircraftText, seat,
                parseEnum(SeatPosition.class, value(r, "seat_position"), "seat_position"),
                parseEnum(CabinClass.class, value(r, "cabin_class"), "cabin_class"),
                parseEnum(TripReason.class, value(r, "reason"), "reason"), notes, parseBoolean(value(r, "cargo")), journey,
                parseEnum(Visibility.class, value(r, "visibility"), "visibility"));
    }

    private static LocalDate parseDate(String text, LocalDate today) {
        if (text == null) {
            throw new RowException("date is missing");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new RowException("date must look like 2024-05-31");
        }
        if (date.isAfter(today)) {
            throw new RowException("date is in the future, only past flights can be imported");
        }
        return date;
    }

    private static String flightNumber(String text) {
        if (text == null) {
            return null;
        }
        String number = text.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!FLIGHT_NUMBER.matcher(number).matches()) {
            throw new RowException("flight_number must look like LH400");
        }
        return number;
    }

    // The airline column wins, otherwise the flight number's prefix has to name exactly one airline
    private Airline airline(String code, String number) {
        String lookup = code != null ? code.toUpperCase(Locale.ROOT) : ReferenceSearchService.prefixOf(number);
        if (lookup == null) {
            throw new RowException("airline is missing and there is no flight number to take it from");
        }
        List<Airline> matches = lookup.length() == 3
                ? airlineRepository.findByIcao(lookup).map(List::of).orElseGet(List::of)
                : airlineRepository.findByIataAndActiveTrue(lookup);
        // A code shared with a cargo arm (LH, Lufthansa Cargo) means the passenger airline
        List<Airline> passenger = matches.stream().filter(a -> !a.looksLikeCargoCarrier()).toList();
        if (matches.size() > 1 && passenger.size() == 1) {
            return passenger.get(0);
        }
        if (matches.size() != 1) {
            throw new RowException(matches.isEmpty() ? "airline " + lookup + " is unknown" : "airline " + lookup + " is ambiguous, use its ICAO code");
        }
        return matches.get(0);
    }

    private Airport airport(String code, String column) {
        if (code == null) {
            throw new RowException(column + " is missing");
        }
        String upper = code.toUpperCase(Locale.ROOT);
        return (upper.length() == 4 ? airportRepository.findByIcao(upper) : airportRepository.findByIata(upper))
                .orElseThrow(() -> new RowException(column + " airport " + upper + " is unknown"));
    }

    private static LocalTime parseTime(String text, String column) {
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text.length() == 4 ? "0" + text : text);
        } catch (DateTimeParseException ex) {
            throw new RowException(column + " must look like 14:05");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String text, String column) {
        if (text == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new RowException(column + " must be one of " + Arrays.toString(type.getEnumConstants()));
        }
    }

    private static boolean parseBoolean(String text) {
        if (text == null) {
            return false;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> throw new RowException("cargo must be yes or no");
        };
    }

    private static String value(Map<String, String> record, String column) {
        String value = record.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Row view(int line, Status status, String message, Map<String, String> r, ImportedFlight f) {
        if (f == null) {
            return new Row(line, status, message, null, value(r, "flight_number"), value(r, "airline"), value(r, "from"), value(r, "to"),
                    value(r, "aircraft"), value(r, "journey"));
        }
        String aircraft = f.aircraftModelRaw() == null ? null
                : f.aircraftType() != null ? f.aircraftType().getName()
                : f.aircraftFamily() != null ? f.aircraftFamily() + " (variant unknown)"
                : f.aircraftModelRaw() + " (unmapped)";
        return new Row(line, status, message, f.date(), f.flightNumber(), f.airline().getName(), code(f.departure()), code(f.arrival()),
                aircraft, f.journey());
    }

    private static String code(Airport airport) {
        return airport.getIata() != null ? airport.getIata() : airport.getIcao();
    }

    // ---- file

    // Strict: size and row caps, UTF-8, the required columns present and nothing unknown
    private List<Map<String, String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("The file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw badRequest("The file is larger than " + (maxBytes / 1024) + " KB");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw badRequest("The file could not be read");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<Map<String, String>> raw = CsvReader.parse(text);
        if (raw.isEmpty()) {
            throw badRequest("The file has no rows under its header");
        }
        if (raw.size() > maxRows) {
            throw badRequest("The file has more than " + maxRows + " rows, split it up");
        }
        List<Map<String, String>> records = new ArrayList<>();
        for (Map<String, String> record : raw) {
            Map<String, String> normalized = new HashMap<>();
            record.forEach((column, value) -> normalized.put(column.trim().toLowerCase(Locale.ROOT), value));
            records.add(normalized);
        }
        Set<String> header = records.get(0).keySet();
        List<String> unknown = header.stream().filter(column -> !COLUMNS.contains(column)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw badRequest("Unknown columns: " + String.join(", ", unknown) + ". Allowed: " + String.join(", ", COLUMNS));
        }
        List<String> missing = REQUIRED.stream().filter(column -> !header.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw badRequest("Missing columns: " + String.join(", ", missing));
        }
        return records;
    }

    private TailwindUser requireTarget(UUID targetUserId) {
        return tailwindUserRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That user has not opted into tailwind"));
    }

    // ---- views

    private CsvImportResponse toResponse(CsvImport run, Map<UUID, String> names) {
        List<CsvImportResponse.RowError> errors = run.getErrors() == null ? List.of()
                : objectMapper.readValue(run.getErrors(), new TypeReference<List<CsvImportResponse.RowError>>() {
                });
        return new CsvImportResponse(run.getId(), run.getAdminUserId(), names.get(run.getAdminUserId()), run.getTargetUserId(),
                names.get(run.getTargetUserId()), run.getFileName(), run.getTotalRows(), run.getImportedRows(), run.getDuplicateRows(),
                run.getErrorRows(), errors, run.getCreatedAt());
    }

    private Map<UUID, String> usernames(Collection<UUID> ids) {
        try {
            return shieldwallUserClient.resolveUsernames(ids);
        } catch (RuntimeException ex) {
            log.warn("could not resolve usernames for the import screen: {}", ex.toString());
            return Map.of();
        }
    }

    private static int count(List<Analyzed> rows, Status status) {
        return (int) rows.stream().filter(r -> r.view().status() == status).count();
    }

    private static String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return null;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class RowException extends RuntimeException {
        RowException(String message) {
            super(message);
        }
    }
}
