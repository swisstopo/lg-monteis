package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record DirectoryUserDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    String firstName,
    String lastName,
    String email) {}
