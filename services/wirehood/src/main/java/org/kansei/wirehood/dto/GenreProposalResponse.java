package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.GenreProposal;
import org.kansei.wirehood.model.GenreProposalStatus;

import java.time.Instant;
import java.util.UUID;

public record GenreProposalResponse(
        UUID id,
        String name,
        UUID submittedBy,
        GenreProposalStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy
) {
    public static GenreProposalResponse from(GenreProposal proposal) {
        return new GenreProposalResponse(
                proposal.getId(),
                proposal.getName(),
                proposal.getSubmittedBy(),
                proposal.getStatus(),
                proposal.getSubmittedAt(),
                proposal.getReviewedAt(),
                proposal.getReviewedBy()
        );
    }
}
