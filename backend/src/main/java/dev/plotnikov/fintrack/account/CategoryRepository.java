package dev.plotnikov.fintrack.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByKindAscNameAsc();

    Optional<Category> findByName(String name);
}
