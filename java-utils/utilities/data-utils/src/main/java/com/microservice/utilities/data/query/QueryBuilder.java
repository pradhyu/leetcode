package com.microservice.utilities.data.query;

import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Dynamic query builder for creating JPA Specifications.
 * Provides a fluent API for building complex queries.
 *
 * @param <T> Entity type
 */
public class QueryBuilder<T> {

    private final List<Specification<T>> specifications = new ArrayList<>();

    /**
     * Create a new QueryBuilder instance
     */
    public static <T> QueryBuilder<T> create() {
        return new QueryBuilder<>();
    }

    /**
     * Add equals condition
     */
    public QueryBuilder<T> equals(String field, Object value) {
        if (value != null) {
            specifications.add((root, query, cb) -> cb.equal(root.get(field), value));
        }
        return this;
    }

    /**
     * Add not equals condition
     */
    public QueryBuilder<T> notEquals(String field, Object value) {
        if (value != null) {
            specifications.add((root, query, cb) -> cb.notEqual(root.get(field), value));
        }
        return this;
    }

    /**
     * Add like condition (case-insensitive)
     */
    public QueryBuilder<T> like(String field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            specifications.add((root, query, cb) -> 
                cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%"));
        }
        return this;
    }

    /**
     * Add starts with condition
     */
    public QueryBuilder<T> startsWith(String field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            specifications.add((root, query, cb) -> 
                cb.like(cb.lower(root.get(field)), value.toLowerCase() + "%"));
        }
        return this;
    }

    /**
     * Add ends with condition
     */
    public QueryBuilder<T> endsWith(String field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            specifications.add((root, query, cb) -> 
                cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase()));
        }
        return this;
    }

    /**
     * Add in condition
     */
    public QueryBuilder<T> in(String field, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            specifications.add((root, query, cb) -> root.get(field).in(values));
        }
        return this;
    }

    /**
     * Add not in condition
     */
    public QueryBuilder<T> notIn(String field, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            specifications.add((root, query, cb) -> cb.not(root.get(field).in(values)));
        }
        return this;
    }

    /**
     * Add greater than condition
     */
    public <Y extends Comparable<? super Y>> QueryBuilder<T> greaterThan(String field, Y value) {
        if (value != null) {
            specifications.add((root, query, cb) -> cb.greaterThan(root.get(field), value));
        }
        return this;
    }

    /**
     * Add greater than or equal condition
     */
    public <Y extends Comparable<? super Y>> QueryBuilder<T> greaterThanOrEqual(String field, Y value) {
        if (value != null) {
            specifications.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), value));
        }
        return this;
    }

    /**
     * Add less than condition
     */
    public <Y extends Comparable<? super Y>> QueryBuilder<T> lessThan(String field, Y value) {
        if (value != null) {
            specifications.add((root, query, cb) -> cb.lessThan(root.get(field), value));
        }
        return this;
    }

    /**
     * Add less than or equal condition
     */
    public <Y extends Comparable<? super Y>> QueryBuilder<T> lessThanOrEqual(String field, Y value) {
        if (value != null) {
            specifications.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), value));
        }
        return this;
    }

    /**
     * Add between condition
     */
    public <Y extends Comparable<? super Y>> QueryBuilder<T> between(String field, Y from, Y to) {
        if (from != null && to != null) {
            specifications.add((root, query, cb) -> cb.between(root.get(field), from, to));
        }
        return this;
    }

    /**
     * Add date range condition
     */
    public QueryBuilder<T> dateRange(String field, LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) {
            specifications.add((root, query, cb) -> cb.between(root.get(field), from, to));
        } else if (from != null) {
            specifications.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), from));
        } else if (to != null) {
            specifications.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), to));
        }
        return this;
    }

    /**
     * Add is null condition
     */
    public QueryBuilder<T> isNull(String field) {
        specifications.add((root, query, cb) -> cb.isNull(root.get(field)));
        return this;
    }

    /**
     * Add is not null condition
     */
    public QueryBuilder<T> isNotNull(String field) {
        specifications.add((root, query, cb) -> cb.isNotNull(root.get(field)));
        return this;
    }

    /**
     * Add is true condition
     */
    public QueryBuilder<T> isTrue(String field) {
        specifications.add((root, query, cb) -> cb.isTrue(root.get(field)));
        return this;
    }

    /**
     * Add is false condition
     */
    public QueryBuilder<T> isFalse(String field) {
        specifications.add((root, query, cb) -> cb.isFalse(root.get(field)));
        return this;
    }

    /**
     * Add join condition
     */
    public QueryBuilder<T> join(String joinField, String field, Object value) {
        if (value != null) {
            specifications.add((root, query, cb) -> {
                Join<T, ?> join = root.join(joinField);
                return cb.equal(join.get(field), value);
            });
        }
        return this;
    }

    /**
     * Add left join condition
     */
    public QueryBuilder<T> leftJoin(String joinField, String field, Object value) {
        if (value != null) {
            specifications.add((root, query, cb) -> {
                Join<T, ?> join = root.join(joinField, JoinType.LEFT);
                return cb.equal(join.get(field), value);
            });
        }
        return this;
    }

    /**
     * Add custom specification
     */
    public QueryBuilder<T> custom(Specification<T> specification) {
        if (specification != null) {
            specifications.add(specification);
        }
        return this;
    }

    /**
     * Add OR condition group
     */
    public QueryBuilder<T> or(QueryBuilder<T>... builders) {
        List<Specification<T>> orSpecs = new ArrayList<>();
        for (QueryBuilder<T> builder : builders) {
            Specification<T> spec = builder.build();
            if (spec != null) {
                orSpecs.add(spec);
            }
        }
        
        if (!orSpecs.isEmpty()) {
            specifications.add((root, query, cb) -> {
                Predicate[] predicates = orSpecs.stream()
                    .map(spec -> spec.toPredicate(root, query, cb))
                    .toArray(Predicate[]::new);
                return cb.or(predicates);
            });
        }
        return this;
    }

    /**
     * Add active (non-deleted) condition
     */
    public QueryBuilder<T> active() {
        specifications.add((root, query, cb) -> cb.equal(root.get("deleted"), false));
        return this;
    }

    /**
     * Add deleted condition
     */
    public QueryBuilder<T> deleted() {
        specifications.add((root, query, cb) -> cb.equal(root.get("deleted"), true));
        return this;
    }

    /**
     * Add created by condition
     */
    public QueryBuilder<T> createdBy(String createdBy) {
        return equals("createdBy", createdBy);
    }

    /**
     * Add updated by condition
     */
    public QueryBuilder<T> updatedBy(String updatedBy) {
        return equals("updatedBy", updatedBy);
    }

    /**
     * Add created after condition
     */
    public QueryBuilder<T> createdAfter(LocalDateTime date) {
        return greaterThan("createdAt", date);
    }

    /**
     * Add created before condition
     */
    public QueryBuilder<T> createdBefore(LocalDateTime date) {
        return lessThan("createdAt", date);
    }

    /**
     * Add updated after condition
     */
    public QueryBuilder<T> updatedAfter(LocalDateTime date) {
        return greaterThan("updatedAt", date);
    }

    /**
     * Add updated before condition
     */
    public QueryBuilder<T> updatedBefore(LocalDateTime date) {
        return lessThan("updatedAt", date);
    }

    /**
     * Build the final Specification
     */
    public Specification<T> build() {
        if (specifications.isEmpty()) {
            return null;
        }
        
        Specification<T> result = specifications.get(0);
        for (int i = 1; i < specifications.size(); i++) {
            result = Specification.where(result).and(specifications.get(i));
        }
        
        return result;
    }

    /**
     * Build with distinct results
     */
    public Specification<T> buildDistinct() {
        Specification<T> spec = build();
        if (spec == null) {
            return (root, query, cb) -> {
                query.distinct(true);
                return cb.conjunction();
            };
        }
        
        return (root, query, cb) -> {
            query.distinct(true);
            return spec.toPredicate(root, query, cb);
        };
    }
}