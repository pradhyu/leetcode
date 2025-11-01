package com.microservice.utilities.data.repository;

import com.microservice.utilities.common.entity.BaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Base repository interface providing common CRUD operations and query methods.
 * Extends JPA repository with additional utility methods for BaseEntity.
 *
 * @param <T> Entity type extending BaseEntity
 * @param <ID> Primary key type
 */
@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID> extends 
        JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * Find all non-deleted entities
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    List<T> findAllActive();

    /**
     * Find all non-deleted entities with pagination
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    Page<T> findAllActive(Pageable pageable);

    /**
     * Find by ID only if not deleted
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    Optional<T> findByIdActive(@Param("id") ID id);

    /**
     * Find entities created after specified date
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdAt > :date AND e.deleted = false")
    List<T> findCreatedAfter(@Param("date") LocalDateTime date);

    /**
     * Find entities modified after specified date
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.updatedAt > :date AND e.deleted = false")
    List<T> findModifiedAfter(@Param("date") LocalDateTime date);

    /**
     * Find entities created by specific user
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdBy = :createdBy AND e.deleted = false")
    List<T> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Find entities modified by specific user
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.updatedBy = :updatedBy AND e.deleted = false")
    List<T> findByUpdatedBy(@Param("updatedBy") String updatedBy);

    /**
     * Soft delete entity by ID
     */
    @Modifying
    @Query("UPDATE #{#entityName} e SET e.deleted = true, e.updatedAt = CURRENT_TIMESTAMP, e.updatedBy = :updatedBy WHERE e.id = :id")
    int softDeleteById(@Param("id") ID id, @Param("updatedBy") String updatedBy);

    /**
     * Soft delete multiple entities by IDs
     */
    @Modifying
    @Query("UPDATE #{#entityName} e SET e.deleted = true, e.updatedAt = CURRENT_TIMESTAMP, e.updatedBy = :updatedBy WHERE e.id IN :ids")
    int softDeleteByIds(@Param("ids") List<ID> ids, @Param("updatedBy") String updatedBy);

    /**
     * Restore soft deleted entity
     */
    @Modifying
    @Query("UPDATE #{#entityName} e SET e.deleted = false, e.updatedAt = CURRENT_TIMESTAMP, e.updatedBy = :updatedBy WHERE e.id = :id")
    int restoreById(@Param("id") ID id, @Param("updatedBy") String updatedBy);

    /**
     * Count active (non-deleted) entities
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.deleted = false")
    long countActive();

    /**
     * Check if entity exists and is active
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    boolean existsByIdActive(@Param("id") ID id);

    /**
     * Find entities with specification (active only)
     */
    default Page<T> findAllActive(Specification<T> spec, Pageable pageable) {
        Specification<T> activeSpec = (root, query, criteriaBuilder) -> 
            criteriaBuilder.equal(root.get("deleted"), false);
        
        Specification<T> combinedSpec = spec != null ? 
            Specification.where(activeSpec).and(spec) : activeSpec;
            
        return findAll(combinedSpec, pageable);
    }

    /**
     * Find entities with specification (active only)
     */
    default List<T> findAllActive(Specification<T> spec) {
        Specification<T> activeSpec = (root, query, criteriaBuilder) -> 
            criteriaBuilder.equal(root.get("deleted"), false);
        
        Specification<T> combinedSpec = spec != null ? 
            Specification.where(activeSpec).and(spec) : activeSpec;
            
        return findAll(combinedSpec);
    }

    /**
     * Batch update entities
     */
    @Modifying
    @Query("UPDATE #{#entityName} e SET e.updatedAt = CURRENT_TIMESTAMP, e.updatedBy = :updatedBy WHERE e.id IN :ids")
    int batchUpdateTimestamp(@Param("ids") List<ID> ids, @Param("updatedBy") String updatedBy);

    /**
     * Find entities by version (for optimistic locking)
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.version = :version AND e.deleted = false")
    List<T> findByVersion(@Param("version") Long version);

    /**
     * Find entities created between dates
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdAt BETWEEN :startDate AND :endDate AND e.deleted = false")
    List<T> findCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                              @Param("endDate") LocalDateTime endDate);

    /**
     * Find entities modified between dates
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.updatedAt BETWEEN :startDate AND :endDate AND e.deleted = false")
    List<T> findModifiedBetween(@Param("startDate") LocalDateTime startDate, 
                               @Param("endDate") LocalDateTime endDate);
}