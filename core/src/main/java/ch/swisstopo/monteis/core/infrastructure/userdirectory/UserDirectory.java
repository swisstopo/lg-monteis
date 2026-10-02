package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.List;
import java.util.UUID;

/** Read access to the users of the identity provider. Nothing returned here is persisted. */
public interface UserDirectory {

  /**
   * The enabled members of the group granting write access to the experiment (its PIs), sorted
   * by last and first name.
   *
   * @throws UserDirectoryUnavailableException if the identity provider cannot be asked
   */
  List<DirectoryUser> principalInvestigatorsOf(UUID experimentId);
}
