package com.hirewise.be.controller;

import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.RequiresOwnership;
import com.hirewise.be.dto.request.CreateOfferRequestDto;
import com.hirewise.be.dto.response.OfferResponseDto;
import com.hirewise.be.dto.response.OfferTemplateResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.OfferService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * M18 - Offer & e-Signature, Recruiter-facing half: UC-36 (create an Offer
 * Letter from a template).
 * <p>
 * RBAC:
 * <ul>
 *   <li>{@code GET  /api/applications/{applicationId}/offer-templates} - {@code OFFER_CREATE},
 *       scoped to the job's department, checked inside the service</li>
 *   <li>{@code POST /api/applications/{applicationId}/offers} - {@code OFFER_CREATE} +
 *       ownership of the parent Job as its Recruiter (RBAC.md section 4:
 *       {@code application.job.recruiter_id}), both enforced by
 *       {@link RequiresOwnership}/{@code OwnershipAspect}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class OfferController {

    OfferService offerService;

    /**
     * UC-36 step 2: templates available in the "Dropdown Offer Template" control.
     *
     * @param applicationId id of the Application an Offer is being created for
     * @param currentUser   authenticated caller, used for authorization
     * @return selectable active offer templates
     */
    @GetMapping("/applications/{applicationId}/offer-templates")
    public ResponseEntity<List<OfferTemplateResponseDto>> listTemplates(
            @PathVariable UUID applicationId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(offerService.listTemplates(applicationId, currentUser));
    }

    /**
     * UC-36 main flow: creates a Draft Offer from the chosen template
     * (BR-OFFER-01/02). Nothing reaches the candidate until UC-37.
     *
     * @param applicationId id of the Application being offered
     * @param request       template, salary, probation rate, start date, answer deadline
     * @param currentUser   authenticated caller - must own the parent Job (as its Recruiter)
     * @return the Draft offer just created
     */
    @PostMapping("/applications/{applicationId}/offers")
    @RequiresOwnership(resourceType = "APPLICATION", idParam = "applicationId",
            permission = PermissionCodes.OFFER_CREATE)
    public ResponseEntity<OfferResponseDto> create(
            @PathVariable UUID applicationId,
            @Valid @RequestBody CreateOfferRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        OfferResponseDto created = offerService.create(applicationId, request, currentUser);
        return ResponseEntity.created(URI.create("/api/offers/" + created.getId())).body(created);
    }

    /**
     * UC-37 step 1: reads back one Offer so the Recruiter can review its
     * rendered body before sending.
     *
     * @param offerId     id of the offer
     * @param currentUser authenticated caller - must own the parent Job
     * @return the offer, including its rendered body
     */
    @GetMapping("/offers/{offerId}")
    @RequiresOwnership(resourceType = "OFFER", idParam = "offerId",
            permission = PermissionCodes.OFFER_CREATE)
    public ResponseEntity<OfferResponseDto> getById(
            @PathVariable UUID offerId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(offerService.getById(offerId));
    }

    /**
     * The Application's most recent Offer, for the Applicant Card to decide
     * whether to show [Tao Offer] or the existing offer's state.
     *
     * @param applicationId id of the Application
     * @param currentUser   authenticated caller - must own the parent Job
     * @return the latest offer, or {@code 204 No Content} if never offered
     */
    @GetMapping("/applications/{applicationId}/offers/latest")
    @RequiresOwnership(resourceType = "APPLICATION", idParam = "applicationId",
            permission = PermissionCodes.OFFER_CREATE)
    public ResponseEntity<OfferResponseDto> getLatestForApplication(
            @PathVariable UUID applicationId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        OfferResponseDto latest = offerService.findLatestForApplication(applicationId);
        return latest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(latest);
    }
}
