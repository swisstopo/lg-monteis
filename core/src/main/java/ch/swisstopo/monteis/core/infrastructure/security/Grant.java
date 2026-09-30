package ch.swisstopo.monteis.core.infrastructure.security;

/**
 * The normalized privilege facts {@link Capabilities} decides on. {@link AccessPolicy} maps each
 * {@code api:*} authority to one grant; {@link #SYSTEM_READ_ALL} has no authority, so no request
 * token can ever carry it (BR4.7).
 */
public enum Grant {
  /** {@code api:experiment:read}: read the assigned experiments. */
  EXPERIMENT_READ,
  /** {@code api:experiment:write}: write the assigned experiments. */
  EXPERIMENT_WRITE,
  /** {@code api:experiment:write-all}: read and write every experiment (global editor). */
  EXPERIMENT_WRITE_ALL,
  /** {@code api:documents:read}. */
  DOCUMENTS_READ,
  /** {@code api:admin}: every experiment plus the admin-only functions. */
  ADMIN,
  /** The system context of background jobs: read every experiment, nothing else. */
  SYSTEM_READ_ALL
}
