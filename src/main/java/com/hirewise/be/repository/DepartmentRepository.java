package com.hirewise.be.repository;

import com.hirewise.be.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * BR-RBAC-06: tra ve chinh departmentId va toan bo phong ban con (moi
     * cap) theo parent_department_id, tinh bang recursive CTE tai thoi diem
     * goi - KHONG luu cung danh sach nay o dau ca de tranh phai dong bo lai
     * khi cay phong ban thay doi.
     */
    @Query(value = """
            WITH RECURSIVE dept_tree AS (
                SELECT department_id FROM departments WHERE department_id = :rootId
                UNION ALL
                SELECT d.department_id FROM departments d
                JOIN dept_tree t ON d.parent_department_id = t.department_id
            )
            SELECT department_id FROM dept_tree
            """, nativeQuery = true)
    List<Long> findSelfAndDescendantIds(@Param("rootId") Long rootId);
}
