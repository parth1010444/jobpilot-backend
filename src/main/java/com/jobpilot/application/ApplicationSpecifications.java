package com.jobpilot.application;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    public static Specification<Application> forUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Application> hasStatus(ApplicationStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Application> search(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + q.trim().toLowerCase() + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("company")), pattern));
            predicates.add(cb.like(cb.lower(root.get("jobTitle")), pattern));
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("location"), "")), pattern));
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern));
            return cb.or(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Application> withFilters(UUID userId, ApplicationStatus status, String q) {
        return forUser(userId).and(hasStatus(status)).and(search(q));
    }
}
