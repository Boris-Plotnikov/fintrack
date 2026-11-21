package dev.plotnikov.fintrack.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAllByOrderByOfficeIdAscNameAsc();

    List<Account> findByOfficeIdAndTypeAndActiveTrue(Long officeId, AccountType type);

    Optional<Account> findByName(String name);
}
