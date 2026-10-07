package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.AccessDenied;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.KeycloakUnavailable;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.Known;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.NoWriteGroup;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns stored owners into the owners a caller gets to see, one experiment after the other. Once
 * Keycloak is unavailable it is not asked again, so a page or an export waits for one timeout and
 * not one per row.
 */
final class VisibleOwnersResolver {

  private static final Logger log = LoggerFactory.getLogger(VisibleOwnersResolver.class);

  private final UserDirectory userDirectory;
  private boolean keycloakUnavailable;

  VisibleOwnersResolver(UserDirectory userDirectory) {
    this.userDirectory = userDirectory;
  }

  VisibleOwners ownersOf(StoredOwners storedOwners) {
    if (!storedOwners.hasOwners()) {
      return VisibleOwners.NONE;
    }
    if (keycloakUnavailable) {
      return VisibleOwners.UNAVAILABLE;
    }
    UUID experimentId = storedOwners.experimentId();
    return switch (userDirectory.principalInvestigatorsOf(experimentId)) {
      case Known known -> onlyOwnersAmong(known.users(), storedOwners);
      case NoWriteGroup _ -> hideOwners(experimentId, "the experiment has no write group");
      case AccessDenied denied -> hideOwners(experimentId, denied.reason());
      case KeycloakUnavailable unavailable -> giveUpOnKeycloak(unavailable.reason());
    };
  }

  private static VisibleOwners onlyOwnersAmong(List<DirectoryUser> pis, StoredOwners storedOwners) {
    return VisibleOwners.of(pis.stream().filter(pi -> storedOwners.isOwner(pi.id())).toList());
  }

  private static VisibleOwners hideOwners(UUID experimentId, String reason) {
    log.warn("Hiding the owners of experiment {}: {}", experimentId, reason);
    return VisibleOwners.NONE;
  }

  private VisibleOwners giveUpOnKeycloak(String reason) {
    log.warn("Showing experiments without owners: {}", reason);
    keycloakUnavailable = true;
    return VisibleOwners.UNAVAILABLE;
  }
}
