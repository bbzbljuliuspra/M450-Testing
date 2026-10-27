package stegmueller.til.centic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import stegmueller.til.centic.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
