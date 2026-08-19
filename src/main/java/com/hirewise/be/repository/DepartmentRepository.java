package com.hirewise.be.repository;

import com.hirewise.be.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@link Department} entities.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * BR-RBAC-06: returns the given departmentId together with all of its
     * descendant departments (at any depth), computed via a recursive CTE
     * at call time. The result is never cached/persisted anywhere, so it
     * doesn't need to be resynced when the department tree changes.
     *
     * @param rootId id of the department to start the traversal from
     * @return ids of {@code rootId} and all its descendant departments
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
