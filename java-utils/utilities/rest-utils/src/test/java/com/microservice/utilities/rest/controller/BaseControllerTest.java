package com.microservice.utilities.rest.controller;

import com.microservice.utilities.common.entity.BaseEntity;
import com.microservice.utilities.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseControllerTest {

    @Mock
    private JpaRepository<TestEntity, Long> repository;

    private TestController controller;

    @BeforeEach
    void setUp() {
        controller = new TestController(repository);
    }

    @Test
    void findAll_ShouldReturnPagedResults() {
        // Given
        List<TestEntity> entities = Arrays.asList(
                createTestEntity(1L, "Entity 1"),
                createTestEntity(2L, "Entity 2")
        );
        Page<TestEntity> page = new PageImpl<>(entities, PageRequest.of(0, 10), 2);
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        ResponseEntity<?> response = controller.findAll(PageRequest.of(0, 10));

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(repository).findAll(any(Pageable.class));
    }

    @Test
    void findById_WhenEntityExists_ShouldReturnEntity() {
        // Given
        TestEntity entity = createTestEntity(1L, "Test Entity");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        // When
        ResponseEntity<?> response = controller.findById(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(repository).findById(1L);
    }

    @Test
    void findById_WhenEntityNotExists_ShouldThrowException() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> controller.findById(1L));
        verify(repository).findById(1L);
    }

    @Test
    void create_ShouldCreateNewEntity() {
        // Given
        TestEntity entity = createTestEntity(null, "New Entity");
        TestEntity savedEntity = createTestEntity(1L, "New Entity");
        when(repository.save(any(TestEntity.class))).thenReturn(savedEntity);

        // When
        ResponseEntity<?> response = controller.create(entity);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(repository).save(any(TestEntity.class));
    }

    @Test
    void update_WhenEntityExists_ShouldUpdateEntity() {
        // Given
        TestEntity entity = createTestEntity(1L, "Updated Entity");
        when(repository.existsById(1L)).thenReturn(true);
        when(repository.save(any(TestEntity.class))).thenReturn(entity);

        // When
        ResponseEntity<?> response = controller.update(1L, entity);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(repository).existsById(1L);
        verify(repository).save(any(TestEntity.class));
    }

    @Test
    void update_WhenEntityNotExists_ShouldThrowException() {
        // Given
        TestEntity entity = createTestEntity(1L, "Updated Entity");
        when(repository.existsById(1L)).thenReturn(false);

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> controller.update(1L, entity));
        verify(repository).existsById(1L);
        verify(repository, never()).save(any(TestEntity.class));
    }

    @Test
    void delete_WhenEntityExists_ShouldDeleteEntity() {
        // Given
        TestEntity entity = createTestEntity(1L, "Test Entity");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(TestEntity.class))).thenReturn(entity);

        // When
        ResponseEntity<?> response = controller.delete(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(repository).findById(1L);
        verify(repository).save(any(TestEntity.class)); // Soft delete
    }

    @Test
    void delete_WhenEntityNotExists_ShouldThrowException() {
        // Given
        when(repository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> controller.delete(1L));
        verify(repository).findById(1L);
        verify(repository, never()).save(any(TestEntity.class));
    }

    @Test
    void exists_WhenEntityExists_ShouldReturnTrue() {
        // Given
        when(repository.existsById(1L)).thenReturn(true);

        // When
        ResponseEntity<?> response = controller.exists(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(repository).existsById(1L);
    }

    @Test
    void count_ShouldReturnEntityCount() {
        // Given
        when(repository.count()).thenReturn(5L);

        // When
        ResponseEntity<?> response = controller.count();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(repository).count();
    }

    private TestEntity createTestEntity(Long id, String name) {
        TestEntity entity = new TestEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }

    // Test entity class
    static class TestEntity extends BaseEntity {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    // Test controller implementation
    static class TestController extends BaseController<TestEntity, Long, JpaRepository<TestEntity, Long>> {

        public TestController(JpaRepository<TestEntity, Long> repository) {
            super(repository, "TestEntity");
        }

        @Override
        protected void updateEntityFields(TestEntity existingEntity, TestEntity partialEntity) {
            if (partialEntity.getName() != null) {
                existingEntity.setName(partialEntity.getName());
            }
        }
    }
}