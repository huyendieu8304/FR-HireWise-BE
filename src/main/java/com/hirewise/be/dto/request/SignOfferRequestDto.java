package com.hirewise.be.dto.request;

import com.hirewise.be.domain.SignatureMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-39 steps 2-3: what the candidate submits from the e-Signature Canvas
 * screen.
 * <p>
 * Which of the two payload fields is required depends on {@link #method}
 * (LV-22), so that pairing is checked in {@code OfferSigningService} rather
 * than by an annotation - a blank signature is ME-34, a business message,
 * not a field-format complaint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignOfferRequestDto {

    @NotNull(message = "{validation.offer_sign.method.required}")
    private SignatureMethod method;

    /**
     * {@code data:image/png;base64,...} of the drawn signature. Required for
     * {@link SignatureMethod#DRAW}. Capped well below the multipart limit -
     * a signature scribble is a few tens of KB, so anything larger is either
     * a mistake or an attempt to store an arbitrary blob through this path.
     */
    @Size(max = 1_400_000, message = "{validation.offer_sign.signature_image.size}")
    private String signatureImageBase64;

    /** The typed name. Required for {@link SignatureMethod#TYPE}. */
    @Size(max = 150, message = "{validation.offer_sign.typed_name.size}")
    private String typedName;
}
