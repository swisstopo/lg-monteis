package ch.swisstopo.monteis.core.modules.notification.domain;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;

public record RecipientCandidate(DirectoryUser user, boolean selected) {}
