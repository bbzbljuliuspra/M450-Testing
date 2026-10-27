package stegmueller.til.centic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import stegmueller.til.centic.model.Category;
import stegmueller.til.centic.repository.CategoryRepository;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepo;

    public List<Category> getAll() {
        return categoryRepo.findAll();
    }

    public Category getById(Long id) {
        return categoryRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Kategorie nicht gefunden: " + id));
    }

    @Transactional
    public Category create(Category category) {
        return categoryRepo.save(category);
    }

    @Transactional
    public Category update(Long id, Category updated) {
        Category existing = getById(id);
        existing.setName(updated.getName());
        existing.setColorCode(updated.getColorCode());
        existing.setGlobalFlag(updated.isGlobalFlag());
        return categoryRepo.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepo.existsById(id)) {
            throw new NoSuchElementException("Kategorie nicht gefunden: " + id);
        }
        categoryRepo.deleteById(id);
    }
}