package org.kansei.tailwind.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceSearchServiceTest {

    @Test
    void escapeLikeNeutralisesWildcardsAndBackslashes() {
        assertThat(ReferenceSearchService.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
        assertThat(ReferenceSearchService.escapeLike("frankfurt")).isEqualTo("frankfurt");
    }

    @Test
    void flightNumberGivesUpTheAirlineCode() {
        assertThat(ReferenceSearchService.prefixOf("LH400")).isEqualTo("LH");
        assertThat(ReferenceSearchService.prefixOf("tk1044")).isEqualTo("TK");
        assertThat(ReferenceSearchService.prefixOf("W63021")).isEqualTo("W6");
        assertThat(ReferenceSearchService.prefixOf(" DLH400 ")).isEqualTo("DLH");
    }

    @Test
    void somethingThatIsNotAFlightNumberHasNoAirlineCode() {
        assertThat(ReferenceSearchService.prefixOf("Lufthansa")).isNull();
        assertThat(ReferenceSearchService.prefixOf("400")).isNull();
        assertThat(ReferenceSearchService.prefixOf(null)).isNull();
    }

    @Test
    void anAirlineIsNotLookedUpForAnUnparseableNumber() {
        ReferenceSearchService service = new ReferenceSearchService(null, null, null, null);

        assertThat(service.airlinesForFlightNumber("not a flight")).isEmpty();
    }

    @Test
    void shortQueryIsRejected() {
        ReferenceSearchService service = new ReferenceSearchService(null, null, null, null);

        assertThatThrownBy(() -> service.searchAirports(" a ", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
