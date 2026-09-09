package com.hirewise.be.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewerBusySlotDto {
    private LocalDate date;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime time;
}
