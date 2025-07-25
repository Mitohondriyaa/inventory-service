package io.github.mitohondriyaa.inventory.repository;


import io.github.mitohondriyaa.inventory.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    boolean existsByProductIdAndQuantityIsGreaterThanEqual(String productId, Integer quantity);
    Optional<Inventory> findByProductId(String productId);
    void deleteByProductId(String productId);
}