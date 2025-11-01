package com.microservice.utilities.data.repository;

import com.microservice.utilities.common.entity.BaseEntity;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Base repository implementation providing common functionality for all repositories.
 * Extends SimpleJpaRepository with additional utility methods.
 *
 * @param <T> Entity type extending BaseEntity
 * @param <ID> Primary key type
 */
@Transactional(readOnly = true)
public class BaseRepositoryImpl<T extends BaseEntity, ID> extends SimpleJpaRepository<T, ID> 
        implements BaseRepository<T, ID> {

    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ID> entityInformation;

    public BaseRepositoryImpl(JpaEntityInformation<T, ID> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.entityInformation = entityInformation;
    }

    @Override
    public List<T> findAllActive() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        query.select(root).where(cb.equal(root.get("deleted"), false));
        
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public Optional<T> findByIdActive(ID id) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        Predicate idPredicate = cb.equal(root.get(entityInformation.getIdAttribute()), id);
        Predicate activePredicate = cb.equal(root.get("deleted"), false);
        
        query.select(root).where(cb.and(idPredicate, activePredicate));
        
        List<T> results = entityManager.createQuery(query).getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<T> findCreatedAfter(LocalDateTime date) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        Predicate datePredicate = cb.greaterThan(root.get("createdAt"), date);
        Predicate activePredicate = cb.equal(root.get("deleted"), false);
        
        query.select(root).where(cb.and(datePredicate, activePredicate));
        
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public List<T> findModifiedAfter(LocalDateTime date) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        Predicate datePredicate = cb.greaterThan(root.get("updatedAt"), date);
        Predicate activePredicate = cb.equal(root.get("deleted"), false);
        
        query.select(root).where(cb.and(datePredicate, activePredicate));
        
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public List<T> findByCreatedBy(String createdBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        Predicate createdByPredicate = cb.equal(root.get("createdBy"), createdBy);
        Predicate activePredicate = cb.equal(root.get("deleted"), false);
        
        query.select(root).where(cb.and(createdByPredicate, activePredicate));
        
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public List<T> findByUpdatedBy(String updatedBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        Predicate updatedByPredicate = cb.equal(root.get("updatedBy"), updatedBy);
        Predicate activePredicate = cb.equal(root.get("deleted"), false);
        
        query.select(root).where(cb.and(updatedByPredicate, activePredicate));
        
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    @Transactional
    public int softDeleteById(ID id, String updatedBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        javax.persistence.criteria.CriteriaUpdate<T> update = cb.createCriteriaUpdate(getDomainClass());
        Root<T> root = update.from(getDomainClass());
        
        update.set(root.get("deleted"), true);\n        update.set(root.get("updatedAt"), LocalDateTime.now());\n        update.set(root.get("updatedBy"), updatedBy);\n        update.where(cb.equal(root.get(entityInformation.getIdAttribute()), id));\n        \n        return entityManager.createQuery(update).executeUpdate();\n    }\n\n    @Override\n    @Transactional\n    public int softDeleteByIds(List<ID> ids, String updatedBy) {\n        if (ids == null || ids.isEmpty()) {\n            return 0;\n        }\n        \n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        javax.persistence.criteria.CriteriaUpdate<T> update = cb.createCriteriaUpdate(getDomainClass());\n        Root<T> root = update.from(getDomainClass());\n        \n        update.set(root.get("deleted"), true);\n        update.set(root.get("updatedAt"), LocalDateTime.now());\n        update.set(root.get("updatedBy"), updatedBy);\n        update.where(root.get(entityInformation.getIdAttribute()).in(ids));\n        \n        return entityManager.createQuery(update).executeUpdate();\n    }\n\n    @Override\n    @Transactional\n    public int restoreById(ID id, String updatedBy) {\n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        javax.persistence.criteria.CriteriaUpdate<T> update = cb.createCriteriaUpdate(getDomainClass());\n        Root<T> root = update.from(getDomainClass());\n        \n        update.set(root.get("deleted"), false);\n        update.set(root.get("updatedAt"), LocalDateTime.now());\n        update.set(root.get("updatedBy"), updatedBy);\n        update.where(cb.equal(root.get(entityInformation.getIdAttribute()), id));\n        \n        return entityManager.createQuery(update).executeUpdate();\n    }\n\n    @Override\n    public long countActive() {\n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        CriteriaQuery<Long> query = cb.createQuery(Long.class);\n        Root<T> root = query.from(getDomainClass());\n        \n        query.select(cb.count(root)).where(cb.equal(root.get("deleted"), false));\n        \n        return entityManager.createQuery(query).getSingleResult();\n    }\n\n    @Override\n    public boolean existsByIdActive(ID id) {\n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        CriteriaQuery<Long> query = cb.createQuery(Long.class);\n        Root<T> root = query.from(getDomainClass());\n        \n        Predicate idPredicate = cb.equal(root.get(entityInformation.getIdAttribute()), id);\n        Predicate activePredicate = cb.equal(root.get("deleted"), false);\n        \n        query.select(cb.count(root)).where(cb.and(idPredicate, activePredicate));\n        \n        return entityManager.createQuery(query).getSingleResult() > 0;\n    }\n\n    @Override\n    @Transactional\n    public int batchUpdateTimestamp(List<ID> ids, String updatedBy) {\n        if (ids == null || ids.isEmpty()) {\n            return 0;\n        }\n        \n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        javax.persistence.criteria.CriteriaUpdate<T> update = cb.createCriteriaUpdate(getDomainClass());\n        Root<T> root = update.from(getDomainClass());\n        \n        update.set(root.get("updatedAt"), LocalDateTime.now());\n        update.set(root.get("updatedBy"), updatedBy);\n        update.where(root.get(entityInformation.getIdAttribute()).in(ids));\n        \n        return entityManager.createQuery(update).executeUpdate();\n    }\n\n    @Override\n    public List<T> findByVersion(Long version) {\n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        CriteriaQuery<T> query = cb.createQuery(getDomainClass());\n        Root<T> root = query.from(getDomainClass());\n        \n        Predicate versionPredicate = cb.equal(root.get("version"), version);\n        Predicate activePredicate = cb.equal(root.get("deleted"), false);\n        \n        query.select(root).where(cb.and(versionPredicate, activePredicate));\n        \n        return entityManager.createQuery(query).getResultList();\n    }\n\n    @Override\n    public List<T> findCreatedBetween(LocalDateTime startDate, LocalDateTime endDate) {\n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        CriteriaQuery<T> query = cb.createQuery(getDomainClass());\n        Root<T> root = query.from(getDomainClass());\n        \n        Predicate datePredicate = cb.between(root.get("createdAt"), startDate, endDate);\n        Predicate activePredicate = cb.equal(root.get("deleted"), false);\n        \n        query.select(root).where(cb.and(datePredicate, activePredicate));\n        \n        return entityManager.createQuery(query).getResultList();\n    }\n\n    @Override\n    public List<T> findModifiedBetween(LocalDateTime startDate, LocalDateTime endDate) {\n        CriteriaBuilder cb = entityManager.getCriteriaBuilder();\n        CriteriaQuery<T> query = cb.createQuery(getDomainClass());\n        Root<T> root = query.from(getDomainClass());\n        \n        Predicate datePredicate = cb.between(root.get("updatedAt"), startDate, endDate);\n        Predicate activePredicate = cb.equal(root.get("deleted"), false);\n        \n        query.select(root).where(cb.and(datePredicate, activePredicate));\n        \n        return entityManager.createQuery(query).getResultList();\n    }\n\n    /**\n     * Batch save entities with optimized performance\n     */\n    @Override\n    @Transactional\n    public <S extends T> List<S> saveAll(Iterable<S> entities) {\n        List<S> result = new ArrayList<>();\n        int i = 0;\n        \n        for (S entity : entities) {\n            result.add(save(entity));\n            i++;\n            \n            // Flush and clear every 20 entities to avoid memory issues\n            if (i % 20 == 0) {\n                entityManager.flush();\n                entityManager.clear();\n            }\n        }\n        \n        return result;\n    }\n\n    /**\n     * Get the domain class\n     */\n    private Class<T> getDomainClass() {\n        return entityInformation.getJavaType();\n    }\n}"