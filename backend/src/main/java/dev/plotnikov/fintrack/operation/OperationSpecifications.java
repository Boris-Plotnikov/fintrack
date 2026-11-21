package dev.plotnikov.fintrack.operation;

import jakarta.persistence.criteria.Predicate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class OperationSpecifications {
    private OperationSpecifications() {}

    public static Specification<Operation> fromFilter(OperationFilter f, ZoneId zone) {
        return (root, query, cb) -> {
            List<Predicate> c = new ArrayList<>();
            if (!Boolean.TRUE.equals(f.includeCorrections())) {
                c.add(cb.isNull(root.get("correctsId")));
            }
            if (f.from() != null) {
                c.add(cb.greaterThanOrEqualTo(root.get("operationAt"), f.from().atStartOfDay(zone).toInstant()));
            }
            if (f.to() != null) {
                c.add(cb.lessThan(root.get("operationAt"), f.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (f.accountId() != null) {
                c.add(cb.equal(root.get("account").get("id"), f.accountId()));
            }
            if (f.officeId() != null) {
                c.add(cb.equal(root.get("account").get("office").get("id"), f.officeId()));
            }
            if (f.categoryId() != null) {
                c.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.kind() != null) {
                c.add(cb.equal(root.get("kind"), f.kind()));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().toLowerCase(Locale.ROOT) + "%";
                c.add(cb.or(cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("counterparty")), like)));
            }
            return cb.and(c.toArray(new Predicate[0]));
        };
    }
}
