package org.kansei.tailwind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kansei.tailwind.dto.AirportRequest;
import org.kansei.tailwind.dto.AirportResponse;
import org.kansei.tailwind.model.Airport;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.AirlineRepository;
import org.kansei.tailwind.repository.AirportRepository;
import org.kansei.tailwind.repository.CountryRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceAdminServiceTest {

    @Mock
    private TailwindUserRepository tailwindUserRepository;
    @Mock
    private AuditPublisher auditPublisher;
    @Mock
    private AirportRepository airportRepository;
    @Mock
    private AirlineRepository airlineRepository;
    @Mock
    private AircraftTypeRepository aircraftTypeRepository;
    @Mock
    private CountryRepository countryRepository;

    private ReferenceAdminService service;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReferenceAdminService(new AdminAuthService(tailwindUserRepository), auditPublisher,
                airportRepository, airlineRepository, aircraftTypeRepository, countryRepository);
    }

    private static AirportRequest airport(String icao, String iata) {
        return new AirportRequest(icao, iata, "Test Airport", "Testville", "RO", 45.0, 25.0, "Europe/Bucharest", null);
    }

    @Test
    void nonAdminIsRejectedBeforeAnythingIsSavedOrAudited() {
        when(tailwindUserRepository.existsById(adminId)).thenReturn(true);

        assertThatThrownBy(() -> service.createAirport(adminId, "USER", airport("LRTS", "TTS")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(airportRepository, never()).saveAndFlush(any());
        verify(auditPublisher, never()).publishWithResolvedUsername(any(), any(), any(), any(), any());
    }

    @Test
    void adminWithoutTailwindRowIsRejected() {
        when(tailwindUserRepository.existsById(adminId)).thenReturn(false);

        assertThatThrownBy(() -> service.createAirport(adminId, "ADMIN", airport("LRTS", "TTS")))
                .isInstanceOf(ResponseStatusException.class);

        verify(airportRepository, never()).saveAndFlush(any());
    }

    @Test
    void createSavesAndAudits() {
        when(tailwindUserRepository.existsById(adminId)).thenReturn(true);
        when(countryRepository.existsById("RO")).thenReturn(true);
        when(airportRepository.saveAndFlush(any(Airport.class))).thenAnswer(inv -> {
            Airport a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });

        AirportResponse response = service.createAirport(adminId, "ADMIN", airport("LRTS", "TTS"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.airportType()).isEqualTo("small_airport");
        verify(auditPublisher).publishWithResolvedUsername(eq("CREATE_AIRPORT"), eq(adminId), eq("AIRPORT"), eq("7"), anyMap());
    }

    @Test
    void airportNeedsAtLeastOneCode() {
        when(tailwindUserRepository.existsById(adminId)).thenReturn(true);

        assertThatThrownBy(() -> service.createAirport(adminId, "ADMIN", airport(null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void unknownCountryIsRejected() {
        when(tailwindUserRepository.existsById(adminId)).thenReturn(true);
        when(countryRepository.existsById("RO")).thenReturn(false);

        assertThatThrownBy(() -> service.createAirport(adminId, "ADMIN", airport("LRTS", "TTS")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void duplicateCodeIsAConflictAndNotAudited() {
        when(tailwindUserRepository.existsById(adminId)).thenReturn(true);
        when(countryRepository.existsById("RO")).thenReturn(true);
        when(airportRepository.saveAndFlush(any(Airport.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.createAirport(adminId, "ADMIN", airport("LRTS", "TTS")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(auditPublisher, never()).publishWithResolvedUsername(any(), any(), any(), any(), any());
    }
}
