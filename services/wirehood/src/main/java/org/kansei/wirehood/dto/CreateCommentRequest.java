package org.kansei.wirehood.dto;

import java.util.UUID;

public record CreateCommentRequest(String body, UUID parentCommentId) {
}
