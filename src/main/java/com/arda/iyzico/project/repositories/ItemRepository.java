package com.arda.iyzico.project.repositories;

import com.arda.iyzico.project.models.Item;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByActiveTrue();
    Optional<Item> findByIdAndActiveTrue(Long id);
}