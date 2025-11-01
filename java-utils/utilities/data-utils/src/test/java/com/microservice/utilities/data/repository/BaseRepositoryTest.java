package com.microservice.utilities.data.repository;

import com.microservice.utilities.common.entity.BaseEntity;
import com.microservice.utilities.data.config.JpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseRepository functionality using a test entity.
 */
@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true"
})
class BaseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TestEntityRepository repository;

    private TestEntity testEntity1;
    private TestEntity testEntity2;
    private TestEntity deletedEntity;

    @BeforeEach
    void setUp() {
        // Create test entities
        testEntity1 = new TestEntity();
        testEntity1.setName("Test Entity 1");
        testEntity1.setDescription("Description 1");
        testEntity1.setCreatedBy("user1");
        testEntity1.setUpdatedBy("user1");

        testEntity2 = new TestEntity();
        testEntity2.setName("Test Entity 2");
        testEntity2.setDescription("Description 2");
        testEntity2.setCreatedBy("user2");
        testEntity2.setUpdatedBy("user2");

        deletedEntity = new TestEntity();
        deletedEntity.setName("Deleted Entity");
        deletedEntity.setDescription("Deleted Description");
        deletedEntity.setDeleted(true);
        deletedEntity.setCreatedBy("user1");
        deletedEntity.setUpdatedBy("user1");

        // Persist entities
        entityManager.persistAndFlush(testEntity1);
        entityManager.persistAndFlush(testEntity2);
        entityManager.persistAndFlush(deletedEntity);
        entityManager.clear();
    }

    @Test
    void findAllActive_ShouldReturnOnlyNonDeletedEntities() {
        // When
        List<TestEntity> activeEntities = repository.findAllActive();

        // Then
        assertEquals(2, activeEntities.size());
        assertTrue(activeEntities.stream().noneMatch(TestEntity::isDeleted));
        assertTrue(activeEntities.stream().anyMatch(e -> e.getName().equals("Test Entity 1")));
        assertTrue(activeEntities.stream().anyMatch(e -> e.getName().equals("Test Entity 2")));
    }

    @Test
    void findAllActive_WithPageable_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 1);

        // When
        Page<TestEntity> page = repository.findAllActive(pageable);

        // Then
        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertFalse(page.getContent().get(0).isDeleted());
    }

    @Test
    void findByIdActive_ShouldReturnEntityIfNotDeleted() {
        // When
        Optional<TestEntity> found = repository.findByIdActive(testEntity1.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(testEntity1.getName(), found.get().getName());
        assertFalse(found.get().isDeleted());
    }

    @Test
    void findByIdActive_ShouldReturnEmptyIfDeleted() {
        // When
        Optional<TestEntity> found = repository.findByIdActive(deletedEntity.getId());

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    void findCreatedAfter_ShouldReturnEntitiesCreatedAfterDate() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusMinutes(1);

        // When
        List<TestEntity> entities = repository.findCreatedAfter(cutoffDate);

        // Then
        assertEquals(2, entities.size()); // Only non-deleted entities
        assertTrue(entities.stream().allMatch(e -> e.getCreatedAt().isAfter(cutoffDate)));
    }

    @Test
    void findByCreatedBy_ShouldReturnEntitiesCreatedByUser() {
        // When
        List<TestEntity> entities = repository.findByCreatedBy("user1");

        // Then
        assertEquals(1, entities.size()); // Only non-deleted entity by user1
        assertEquals("Test Entity 1", entities.get(0).getName());
    }

    @Test
    void softDeleteById_ShouldMarkEntityAsDeleted() {
        // When
        int updated = repository.softDeleteById(testEntity1.getId(), "admin");

        // Then
        assertEquals(1, updated);
        
        entityManager.clear();
        TestEntity entity = entityManager.find(TestEntity.class, testEntity1.getId());
        assertTrue(entity.isDeleted());
        assertEquals("admin", entity.getUpdatedBy());
    }

    @Test
    void softDeleteByIds_ShouldMarkMultipleEntitiesAsDeleted() {
        // Given
        List<Long> ids = Arrays.asList(testEntity1.getId(), testEntity2.getId());

        // When
        int updated = repository.softDeleteByIds(ids, "admin");

        // Then
        assertEquals(2, updated);
        
        entityManager.clear();
        TestEntity entity1 = entityManager.find(TestEntity.class, testEntity1.getId());
        TestEntity entity2 = entityManager.find(TestEntity.class, testEntity2.getId());
        
        assertTrue(entity1.isDeleted());
        assertTrue(entity2.isDeleted());
        assertEquals("admin", entity1.getUpdatedBy());
        assertEquals("admin", entity2.getUpdatedBy());
    }

    @Test
    void restoreById_ShouldRestoreDeletedEntity() {
        // When
        int updated = repository.restoreById(deletedEntity.getId(), "admin");

        // Then
        assertEquals(1, updated);
        
        entityManager.clear();
        TestEntity entity = entityManager.find(TestEntity.class, deletedEntity.getId());
        assertFalse(entity.isDeleted());
        assertEquals("admin", entity.getUpdatedBy());
    }

    @Test
    void countActive_ShouldReturnCountOfNonDeletedEntities() {
        // When
        long count = repository.countActive();

        // Then
        assertEquals(2, count);
    }

    @Test
    void existsByIdActive_ShouldReturnTrueForActiveEntity() {
        // When
        boolean exists = repository.existsByIdActive(testEntity1.getId());

        // Then
        assertTrue(exists);
    }

    @Test
    void existsByIdActive_ShouldReturnFalseForDeletedEntity() {
        // When
        boolean exists = repository.existsByIdActive(deletedEntity.getId());

        // Then
        assertFalse(exists);
    }

    @Test
    void batchUpdateTimestamp_ShouldUpdateMultipleEntities() {
        // Given
        List<Long> ids = Arrays.asList(testEntity1.getId(), testEntity2.getId());

        // When
        int updated = repository.batchUpdateTimestamp(ids, "admin");

        // Then
        assertEquals(2, updated);
        
        entityManager.clear();
        TestEntity entity1 = entityManager.find(TestEntity.class, testEntity1.getId());
        TestEntity entity2 = entityManager.find(TestEntity.class, testEntity2.getId());
        
        assertEquals("admin", entity1.getUpdatedBy());
        assertEquals("admin", entity2.getUpdatedBy());
    }

    @Test
    void findCreatedBetween_ShouldReturnEntitiesInDateRange() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);

        // When
        List<TestEntity> entities = repository.findCreatedBetween(start, end);

        // Then
        assertEquals(2, entities.size()); // Only non-deleted entities
        assertTrue(entities.stream().allMatch(e -> 
            e.getCreatedAt().isAfter(start) && e.getCreatedAt().isBefore(end)));
    }

    @Test
    void saveAll_ShouldBatchSaveEntities() {
        // Given
        TestEntity entity3 = new TestEntity();
        entity3.setName("Test Entity 3");
        entity3.setDescription("Description 3");

        TestEntity entity4 = new TestEntity();
        entity4.setName("Test Entity 4");
        entity4.setDescription("Description 4");

        List<TestEntity> entities = Arrays.asList(entity3, entity4);

        // When
        List<TestEntity> saved = repository.saveAll(entities);

        // Then
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(e -> e.getId() != null));
        
        long totalCount = repository.count();
        assertEquals(5, totalCount); // 3 original + 2 new
    }

    /**
     * Test entity for repository testing
     */
    @Entity
    @Table(name = "test_entities")
    public static class TestEntity extends BaseEntity {
        
        @Column(name = "name")
        private String name;
        
        @Column(name = "description")
        private String description;

        // Constructors
        public TestEntity() {}

        public TestEntity(String name, String description) {
            this.name = name;
            this.description = description;
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * Test repository interface
     */
    public interface TestEntityRepository extends BaseRepository<TestEntity, Long> {
    }
}