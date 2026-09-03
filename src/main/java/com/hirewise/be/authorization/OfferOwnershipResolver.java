package com.hirewise.be.authorization;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.OfferRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RBAC Layer 4 resolver for {@code resourceType = "OFFER"} (UC-36/UC-37,
 * {@code OFFER_CREATE}/{@code OFFER_SEND} both {@code requiresOwnership = true}
 * for RECRUITER). An Offer has no owner field of its own - ownership is
 * inherited two hops up, through its Application's parent
 * {@link JobPosition} ({@code offer.application.job.recruiter_id}), exactly
 * like {@link ApplicationOwnershipResolver} does one hop up.
 */
@Component
public class OfferOwnershipResolver implements OwnershipResolver {

    private final OfferRepository offerRepository;

    public OfferOwnershipResolver(OfferRepository offerRepository) {
        this.offerRepository = offerRepository;
    }

    @Override
    public String resourceType() {
        return "OFFER";
    }

    /**
     * @param resourceId the Offer's id, as a {@link UUID} (passed in by
     *                    {@link OwnershipAspect} from the controller's
     *                    {@code offerId} path variable)
     * @throws ResourceNotFoundException if no Offer exists with this id
     */
    @Override
    public OwnedResource resolve(Object resourceId) {
        UUID offerId = (UUID) resourceId;
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OFFER_NOT_FOUND, offerId));

        JobPosition job = offer.getApplication().getJobPosition();
        Long ownerId = job.getRecruiter() != null ? job.getRecruiter().getId() : null;
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        return new OwnedResource(ownerId, departmentId, job.getId());
    }
}
