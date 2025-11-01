package com.microservice.utilities.rest.controller;

import com.microservice.utilities.common.dto.ApiResponse;
import com.microservice.utilities.common.dto.PaginationInfo;
import com.microservice.utilities.common.entity.BaseEntity;
import com.microservice.utilities.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Base controller providing common CRUD operations for REST endpoints.
 * Extends this class to get standard CRUD functionality with consistent API responses.
 *
 * @param <T> Entity type extending BaseEntity
 * @param <ID> Primary key type
 * @param <R> Repository type extending JpaRepository
 */
public abstract class BaseController<T extends BaseEntity, ID, R extends JpaRepository<T, ID>> {

    protected final R repository;
    protected final String entityName;

    protected BaseController(R repository, String entityName) {
        this.repository = repository;
        this.entityName = entityName;
    }

    /**
     * Get all entities with pagination support
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<T>>> findAll(Pageable pageable) {
        Page<T> page = repository.findAll(pageable);
        PaginationInfo pagination = PaginationInfo.of(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
        
        ApiResponse<List<T>> response = ApiResponse.success(
                page.getContent(),
                "Retrieved " + entityName + " list successfully",
                pagination
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get entity by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<T>> findById(@PathVariable ID id) {
        Optional<T> entity = repository.findById(id);
        
        if (entity.isEmpty()) {
            throw ResourceNotFoundException.forId(entityName, id);
        }
        
        ApiResponse<T> response = ApiResponse.success(
                entity.get(),
                entityName + " retrieved successfully"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Create new entity
     */
    @PostMapping
    public ResponseEntity<ApiResponse<T>> create(@Valid @RequestBody T entity) {
        // Ensure it's a new entity
        entity.setId(null);
        
        T savedEntity = repository.save(entity);
        
        ApiResponse<T> response = ApiResponse.success(
                savedEntity,
                entityName + " created successfully"
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update existing entity
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<T>> update(@PathVariable ID id, @Valid @RequestBody T entity) {
        if (!repository.existsById(id)) {
            throw ResourceNotFoundException.forId(entityName, id);
        }
        
        entity.setId((Long) id); // Cast to Long for BaseEntity
        T savedEntity = repository.save(entity);
        
        ApiResponse<T> response = ApiResponse.success(
                savedEntity,
                entityName + " updated successfully"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Partially update entity
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<T>> partialUpdate(@PathVariable ID id, @RequestBody T partialEntity) {
        Optional<T> existingEntity = repository.findById(id);
        
        if (existingEntity.isEmpty()) {
            throw ResourceNotFoundException.forId(entityName, id);
        }
        
        T entityToUpdate = existingEntity.get();
        updateEntityFields(entityToUpdate, partialEntity);
        
        T savedEntity = repository.save(entityToUpdate);
        
        ApiResponse<T> response = ApiResponse.success(
                savedEntity,
                entityName + " updated successfully"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete entity by ID (soft delete if supported)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable ID id) {
        Optional<T> entity = repository.findById(id);
        
        if (entity.isEmpty()) {
            throw ResourceNotFoundException.forId(entityName, id);
        }
        
        T entityToDelete = entity.get();
        
        // Use soft delete if entity supports it
        if (supportsSoftDelete()) {
            entityToDelete.markAsDeleted();
            repository.save(entityToDelete);
        } else {
            repository.deleteById(id);
        }
        
        ApiResponse<Void> response = ApiResponse.success(entityName + " deleted successfully");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Check if entity exists
     */
    @GetMapping("/{id}/exists")
    public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable ID id) {
        boolean exists = repository.existsById(id);
        
        ApiResponse<Boolean> response = ApiResponse.success(
                exists,
                entityName + " existence check completed"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get total count of entities
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> count() {
        long count = repository.count();
        
        ApiResponse<Long> response = ApiResponse.success(
                count,
                entityName + " count retrieved successfully"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Override this method to implement partial update logic
     */
    protected abstract void updateEntityFields(T existingEntity, T partialEntity);

    /**
     * Override this method to indicate if the entity supports soft delete
     */
    protected boolean supportsSoftDelete() {
        return true; // BaseEntity supports soft delete by default
    }

    /**
     * Override this method to add custom validation before create
     */
    protected void validateForCreate(T entity) {
        // Default implementation - no additional validation
    }

    /**
     * Override this method to add custom validation before update
     */
    protected void validateForUpdate(T entity) {
        // Default implementation - no additional validation
    }

    /**
     * Override this method to perform actions after successful create
     */
    protected void afterCreate(T entity) {
        // Default implementation - no additional actions
    }

    /**
     * Override this method to perform actions after successful update
     */
    protected void afterUpdate(T entity) {
        // Default implementation - no additional actions
    }

    /**
     * Override this method to perform actions after successful delete
     */
    protected void afterDelete(ID id) {
        // Default implementation - no additional actions
    }
}