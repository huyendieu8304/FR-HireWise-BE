package com.hirewise.be.security;

import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.KeycloakSyncException;
import com.hirewise.be.logging.LogMaskUtils;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Client for talking to the Keycloak Admin REST API, using the official
 * {@code org.keycloak:keycloak-admin-client} library (instead of hand-rolling
 * RestClient calls and manually fetching/refreshing the admin access token
 * as the previous version did) - this library already wraps token
 * acquisition via the Client Credentials grant and auto-refreshes it on
 * expiry (through {@link Keycloak}'s internal {@code TokenManager}), so it
 * only needs to be built once and reused as a singleton bean (thread-safe).
 * <p>
 * Uses a dedicated Service Account (client credentials grant, with the
 * "manage-users" role on the HireWise realm - see setup-keycloak.md
 * section 2.3), NOT the shared "hirewise-be" client (resource server) nor
 * the FE's public client (see FE-Keycloak-Setup.md).
 * <p>
 * Provides 4 main capabilities:
 * <ol>
 *   <li>createUser / deleteUser           : syncs user creation/deletion (UC-02).</li>
 *   <li>forceLogout                       : forced logout (best-effort).</li>
 *   <li>assignRealmRole / revokeRealmRole : syncs role grant/revoke (UC-03).</li>
 * </ol>
 */
@Slf4j
@Component
public class KeycloakAdminClient {

    /** requiredAction that marks an account as not having a password yet -
     * Keycloak will force the user to set one on first login, which we use
     * as the "activation" mechanism (EM-01) together with
     * {@code executeActionsEmail}. */
    private static final List<String> ACTIVATION_REQUIRED_ACTIONS = List.of("UPDATE_PASSWORD");

    private final Keycloak adminClient;
    private final String realm;
    private final boolean configured;

    public KeycloakAdminClient(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${app.keycloak.admin.client-id:}") String adminClientId,
            @Value("${app.keycloak.admin.client-secret:}") String adminClientSecret) {
        String realmBaseUrl = issuerUri.substring(0, issuerUri.indexOf("/realms/"));
        this.realm = issuerUri.substring(issuerUri.indexOf("/realms/") + "/realms/".length());
        this.configured = !adminClientId.isBlank() && !adminClientSecret.isBlank();

        // KeycloakBuilder.build() does NOT make any network call itself - fetching
        // the access token (and auto-refreshing it on expiry) only happens
        // "lazily" on the first actual API call, through the library's internal
        // TokenManager. It is therefore safe to build this once here regardless
        // of whether app.keycloak.admin.* is actually configured (every method
        // that really needs to call Keycloak checks `configured` first - see
        // the methods below).
        this.adminClient = KeycloakBuilder.builder()
                .serverUrl(realmBaseUrl)
                .realm(realm)
                .clientId(configured ? adminClientId : "unconfigured")
                .clientSecret(adminClientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    private RealmResource realmResource() {
        return adminClient.realm(realm);
    }

    /**
     * UC-02: Creates a new user in Keycloak (HR Admin onboarding a new
     * hire).
     * <p>
     * This is a MANDATORY synchronous operation (unlike {@code forceLogout})
     * - on failure it MUST throw {@link KeycloakSyncException} so
     * {@code UserAdminService} rolls back the transaction; we must never
     * write an internal {@code users} record while we're not certain the
     * Keycloak account actually exists.
     * <p>
     * Setting {@code requiredActions=UPDATE_PASSWORD} makes Keycloak treat
     * this as an account with no password yet; the subsequent call to
     * {@code executeActionsEmail} makes Keycloak send the first-time
     * "set your password" email itself (acting as the EM-01 activation
     * email, using Keycloak's already-configured SMTP - HireWise-BE does
     * not need its own SMTP setup for this flow).
     *
     * @param email    email address of the new user; also used as the Keycloak username
     * @param fullName display name to set as the user's first name in Keycloak
     * @return the newly created user's keycloakId (UUID), stored as {@code users.keycloak_id}
     * @throws KeycloakSyncException if the admin client is not configured, or the create call fails
     */
    public String createUser(String email, String fullName) {
        if (!configured) {
            log.error("Keycloak admin client is not configured (app.keycloak.admin.client-id/secret) - "
                    + "cannot create Keycloak user for email={}", LogMaskUtils.maskEmail(email));
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_USER_SYNC_FAILED, email);
        }

        UsersResource usersResource = realmResource().users();

        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(email);
        newUser.setEmail(email);
        newUser.setFirstName(fullName);
        newUser.setEnabled(true);
        newUser.setEmailVerified(false);
        newUser.setRequiredActions(ACTIVATION_REQUIRED_ACTIONS);

        try (Response response = usersResource.create(newUser)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                log.error("Failed to create Keycloak user for email={}: HTTP {}",
                        LogMaskUtils.maskEmail(email), response.getStatus());
                throw new KeycloakSyncException(ErrorCode.KEYCLOAK_USER_SYNC_FAILED, email);
            }
            String keycloakId = CreatedResponseUtil.getCreatedId(response);
            triggerActivationEmail(usersResource, keycloakId);
            return keycloakId;
        } catch (KeycloakSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Keycloak user for email={}: {}", LogMaskUtils.maskEmail(email), e.getMessage());
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_USER_SYNC_FAILED, email);
        }
    }

    /**
     * Triggers Keycloak to send the "set your password" first-login email
     * (EM-01) for a newly created user. This is best-effort SEPARATELY from
     * createUser(): if this step fails (e.g. the realm has no SMTP
     * configured), the Keycloak user has still been created successfully -
     * the HR Admin can manually click "Resend activation email" in the
     * Admin Console later, no need to roll back the user creation.
     */
    private void triggerActivationEmail(UsersResource usersResource, String keycloakId) {
        try {
            usersResource.get(keycloakId).executeActionsEmail(ACTIVATION_REQUIRED_ACTIONS);
        } catch (Exception e) {
            log.warn("Failed to send activation email (EM-01) for keycloakUserId={}: {}", keycloakId, e.getMessage());
        }
    }

    /**
     * Compensating action for createUser(): deletes the "orphaned" Keycloak
     * user if the subsequent internal DB write fails (e.g. a transient DB
     * outage) - see {@code UserAdminService#create}. Best-effort - only
     * logs the error, does NOT throw (so it doesn't mask the original
     * exception that triggered the rollback).
     *
     * @param keycloakUserId the orphaned Keycloak user id to remove; a no-op if {@code null}
     */
    public void deleteUser(String keycloakUserId) {
        if (!configured || keycloakUserId == null) {
            return;
        }
        try {
            // Uses UserResource#remove() (called per-user, returns void) instead of
            // UsersResource#delete(id) - more stable across library versions since
            // it doesn't depend on a specific return type (Response vs void).
            realmResource().users().get(keycloakUserId).remove();
        } catch (Exception e) {
            log.error("Compensate: failed to delete orphaned Keycloak user for keycloakUserId={} - "
                    + "MANUAL CLEANUP REQUIRED in the Admin Console: {}", keycloakUserId, e.getMessage());
        }
    }

    /**
     * Force-terminates every active session for a user in Keycloak (revokes
     * tokens - BR-AUTH-04).
     * <p>
     * Note: this is an ADDITIONAL defense-in-depth measure, best-effort by
     * policy. If it fails (Keycloak unreachable, missing config, ...), only
     * a warning is logged - it does NOT throw, so it never interrupts the
     * primary account-lock flow ({@code users.status} has already been set
     * to BLOCKED/DISABLED successfully in the internal DB by this point -
     * that is the actual source of truth for BR-AUTH-07).
     *
     * @param keycloakUserId the Keycloak user whose sessions should be terminated
     */
    public void forceLogout(String keycloakUserId) {
        if (!configured) {
            log.warn("Keycloak admin client is not configured (app.keycloak.admin.client-id/secret) - "
                    + "skipping force-logout for keycloakUserId={}", keycloakUserId);
            return;
        }
        try {
            realmResource().users().get(keycloakUserId).logout();
        } catch (Exception e) {
            log.warn("Keycloak force-logout failed for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
        }
    }

    /**
     * UC-03: Assigns a Realm Role to a user in Keycloak (keeping it in sync
     * with the internal system's permissions). Mandatory synchronous
     * operation - on failure it MUST throw KeycloakSyncException so the
     * internal DB write is rolled back (see
     * {@code RoleAssignmentService#assignRole}).
     *
     * @param keycloakUserId Keycloak user to grant the role to
     * @param roleCode       realm role name, must already exist in Keycloak
     * @throws KeycloakSyncException if the admin client is not configured, the role does not exist, or the call fails
     */
    public void assignRealmRole(String keycloakUserId, String roleCode) {
        if (!configured) {
            log.error("Keycloak admin client is not configured - cannot assign role '{}' "
                    + "in Keycloak for keycloakUserId={}", roleCode, keycloakUserId);
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
        try {
            RoleRepresentation role = fetchRealmRole(roleCode);
            realmResource().users().get(keycloakUserId).roles().realmLevel().add(List.of(role));
        } catch (KeycloakSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to assign realm role '{}' for keycloakUserId={}: {}", roleCode, keycloakUserId, e.getMessage());
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
    }

    /**
     * UC-03/AF-01: Revokes (removes) a Realm Role previously assigned to a
     * user in Keycloak - the counterpart of {@link #assignRealmRole}. Also
     * a MANDATORY synchronous operation: on failure, the internal DB must
     * not be treated as having revoked it (to avoid a situation where the
     * DB says "access removed" while the user's next-login JWT from
     * Keycloak still carries the old role because Keycloak never actually
     * removed it) - see {@code RoleAssignmentService#revokeRole}.
     *
     * @param keycloakUserId Keycloak user to revoke the role from
     * @param roleCode       realm role name, must already exist in Keycloak
     * @throws KeycloakSyncException if the admin client is not configured, the role does not exist, or the call fails
     */
    public void revokeRealmRole(String keycloakUserId, String roleCode) {
        if (!configured) {
            log.error("Keycloak admin client is not configured - cannot revoke role '{}' "
                    + "in Keycloak for keycloakUserId={}", roleCode, keycloakUserId);
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
        try {
            RoleRepresentation role = fetchRealmRole(roleCode);
            realmResource().users().get(keycloakUserId).roles().realmLevel().remove(List.of(role));
        } catch (KeycloakSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to revoke realm role '{}' for keycloakUserId={}: {}", roleCode, keycloakUserId, e.getMessage());
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
    }

    /**
     * Looks up the Role Representation (which carries Keycloak's internal
     * id for that role) by role name - shared by both assign and revoke.
     */
    private RoleRepresentation fetchRealmRole(String roleCode) {
        try {
            return realmResource().roles().get(roleCode).toRepresentation();
        } catch (NotFoundException e) {
            log.error("Realm role '{}' not found in Keycloak - this role must be created first "
                    + "(see setup-keycloak.md section 2.2)", roleCode);
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
    }
}
