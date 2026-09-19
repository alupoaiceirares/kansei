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
    void shortQueryIsRejected() {
        ReferenceSearchService service = new ReferenceSearchService(null, null, null, null);

        assertThatThrownBy(() -> service.searchAirports(" a ", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
