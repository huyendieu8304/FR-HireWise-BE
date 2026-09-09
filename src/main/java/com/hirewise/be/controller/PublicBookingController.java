package com.hirewise.be.controller;

import com.hirewise.be.dto.request.ConfirmBookingSlotRequestDto;
import com.hirewise.be.dto.response.BookingConfirmResponseDto;
import com.hirewise.be.dto.response.BookingPageResponseDto;
import com.hirewise.be.service.InterviewService;
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

import java.util.UUID;

/**
 * Public candidate-facing endpoints for Self-service Interview Booking:
 * - UC-34: Candidate views the booking page with open slots
 * - UC-35: Candidate confirms their chosen slot
 * <p>
 * These endpoints are public (permitAll in SecurityConfig) and secured by the unique bookingToken UUID.
 */
@RestController
@RequestMapping("/api/public/booking")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PublicBookingController {

    InterviewService interviewService;

    /**
     * UC-34: Returns details for the public booking page.
     *
     * @param token unique UUID token from candidate's booking link
     * @return candidate & job position info and list of available slots
     */
    @GetMapping("/{token}")
    public ResponseEntity<BookingPageResponseDto> getBookingPage(@PathVariable UUID token) {
        return ResponseEntity.ok(interviewService.getBookingPage(token));
    }

}
