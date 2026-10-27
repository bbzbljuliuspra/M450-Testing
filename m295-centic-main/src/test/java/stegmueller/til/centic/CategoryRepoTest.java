package stegmueller.til.centic;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.annotation.Rollback;
import stegmueller.til.centic.model.Category;
import stegmueller.til.centic.repository.CategoryRepository;

import java.util.List;
import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Rollback(false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    private Long createdCategoryId;

    // Create
    @Test
    @Order(1)
    void testSave() {
        Category category = Category.builder()
                .name("Lebensmittel")
                .colorCode("#00FF00")
                .globalFlag(true)
                .build();

        Category saved = categoryRepository.save(category);

        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals("Lebensmittel", saved.getName());
        Assertions.assertEquals("#00FF00", saved.getColorCode());
        Assertions.assertTrue(saved.isGlobalFlag());

        createdCategoryId = saved.getId();
    }

    // Read

    @Test
    @Order(2)
    void testFindAll() {
        List<Category> categories = categoryRepository.findAll();

        Assertions.assertFalse(categories.isEmpty());
    }

    @Test
    @Order(3)
    void testFindById() {
        Optional<Category> result = categoryRepository.findById(createdCategoryId);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals("Lebensmittel", result.get().getName());
    }

    @Test
    @Order(4)
    void testFindById_notFound() {
        Optional<Category> result = categoryRepository.findById(999L);

        Assertions.assertFalse(result.isPresent());
    }

    // Update
    @Test
    @Order(5)
    void testUpdate() {
        Category existing = categoryRepository.findById(createdCategoryId).orElseThrow();
        existing.setName("Lebensmittel aktualisiert");
        existing.setColorCode("#FF0000");

        Category updated = categoryRepository.save(existing);

        Assertions.assertEquals("Lebensmittel aktualisiert", updated.getName());
        Assertions.assertEquals("#FF0000", updated.getColorCode());
    }

    // Delete
    @Test
    @Order(6)
    void testDelete() {
        categoryRepository.deleteById(createdCategoryId);

        Optional<Category> result = categoryRepository.findById(createdCategoryId);
        Assertions.assertFalse(result.isPresent());
    }
}