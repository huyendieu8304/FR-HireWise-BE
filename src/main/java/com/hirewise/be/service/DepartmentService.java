package com.hirewise.be.service;

import com.hirewise.be.dto.response.DepartmentResponseDto;
import com.hirewise.be.mapper.DepartmentMapper;
import com.hirewise.be.repository.DepartmentRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class DepartmentService {

    DepartmentRepository departmentRepository;

    /**
     * Lists active departments, ordered by name.
     *
     * @return active departments; empty if none are configured yet
     */
    public List<DepartmentResponseDto> listActive() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(DepartmentMapper::toResponseDto)
                .toList();
    }
}
