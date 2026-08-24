package com.hirewise.be.controller;

import com.hirewise.be.dto.response.DepartmentResponseDto;
import com.hirewise.be.service.DepartmentService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Department controller
 * Requires a * valid access token (see {@code SecurityConfig#anyRequest().authenticated()})
 */
@RestController
@RequestMapping("/api/departments")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class DepartmentController {

    DepartmentService departmentService;

    /**
     * Lists active departments, ordered by name.
     *
     * @return active departments
     */
    @GetMapping
    public ResponseEntity<List<DepartmentResponseDto>> list() {
        return ResponseEntity.ok(departmentService.listActive());
    }
}
