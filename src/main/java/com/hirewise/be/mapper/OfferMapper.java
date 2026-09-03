package com.hirewise.be.mapper;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferTemplate;
import com.hirewise.be.dto.response.OfferResponseDto;
import com.hirewise.be.dto.response.OfferTemplateResponseDto;

/** Entity to DTO conversion for the Offer & e-Signature module (UC-36..UC-39). */
public final class OfferMapper {

    private OfferMapper() {
    }

    /**
     * Maps one Offer for the Recruiter-facing endpoints.
     *
     * @param offer the offer to convert; its Application, Candidate, Job and
     *              template associations must be reachable (same transaction)
     */
    public static OfferResponseDto toDto(Offer offer) {
        return OfferResponseDto.builder()
                .id(offer.getId())
                .applicationId(offer.getApplication().getId())
                .candidateName(offer.getApplication().getCandidate().getFullName())
                .jobTitle(offer.getApplication().getJobPosition().getTitle())
                .offerTemplateId(offer.getOfferTemplate().getId())
                .offerTemplateName(offer.getOfferTemplate().getName())
                .salary(offer.getSalary())
                .probationRate(offer.getProbationRate())
                .startDate(offer.getStartDate())
                .expiresAt(offer.getExpiresAt())
                .status(offer.getStatus().name())
                .renderedBody(offer.getRenderedBody())
                .sentAt(offer.getSentAt())
                .signedAt(offer.getSignedAt())
                .createdAt(offer.getCreatedAt())
                .build();
    }

    /** Maps one selectable template for UC-36's dropdown. */
    public static OfferTemplateResponseDto toTemplateDto(OfferTemplate template) {
        return OfferTemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .version(template.getVersion())
                .bodyTemplate(template.getBodyTemplate())
                .departmentId(template.getDepartment() == null ? null : template.getDepartment().getId())
                .build();
    }
}
