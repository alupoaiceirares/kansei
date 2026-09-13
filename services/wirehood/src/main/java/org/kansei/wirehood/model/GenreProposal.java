package org.kansei.wirehood.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A crowd-proposed new genre, pending admin review - approving one inserts a real row into the
 * fixed/seeded genres table, see GenreProposalService.approve
 */
@Table("genre_proposals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenreProposal {

    @Id
    private UUID id;

    private String name;

    private UUID submittedBy;

    @Builder.Default
    private GenreProposalStatus status = GenreProposalStatus.PENDING;

    private Instant submittedAt;

    private Instant reviewedAt;

    private UUID reviewedBy;
}
