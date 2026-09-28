package ch.swisstopo.monteis.core.modules.identity.web.dto;

import java.util.List;
import java.util.UUID;

public record CurrentUserDto(
    boolean canWrite, boolean canWriteAllExperiments, List<UUID> writeExperimentIds) {}
