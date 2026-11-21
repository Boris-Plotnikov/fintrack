package dev.plotnikov.fintrack.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationRepository extends JpaRepository<Operation, Long>, JpaSpecificationExecutor<Operation> {
    @Query("select coalesce(sum(o.amount), 0) from Operation o where o.account.id = :accountId")
    BigDecimal sumByAccount(@Param("accountId") Long accountId);

    List<Operation> findByCorrectsIdOrderByCreatedAt(Long correctsId);

    List<Operation> findByCorrectsIdIn(List<Long> ids);

    List<Operation> findByOperationAtBetween(Instant from, Instant to);

    List<Operation> findByAccountIdOrderByOperationAtDesc(Long accountId);

    boolean existsByAccountIdAndOperationAtAndAmountAndDescription(Long accountId, Instant at, BigDecimal amount,
                                                                    String description);
}
