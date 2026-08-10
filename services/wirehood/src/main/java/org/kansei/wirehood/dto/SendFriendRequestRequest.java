package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SendFriendRequestRequest(@NotNull UUID userId) {
}
