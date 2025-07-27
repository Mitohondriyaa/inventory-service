package io.github.mitohondriyaa.inventory.repository;


import io.github.mitohondriyaa.inventory.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    boolean existsByProductIdAndQuantityIsGreaterThanEqual(String productId, Integer quantity);
    Optional<Inventory> findByProductId(String productId);
    @Transactional
    void deleteByProductId(String productId);
    @Transactional
    @Modifying
    @Query("""
        UPDATE Inventory i
        SET i.quantity = i.quantity - :quantity
        WHERE i.productId = :productId AND i.quantity >= :quantity
""")
    Integer decreaseQuantityIfEnough(
        @Param("productId") String productId,
        @Param("quantity") Integer quantity
    );
    @Transactional
    @Modifying
    @Query("""
        UPDATE Inventory i
        SET i.quantity = i.quantity + :quantity
        WHERE i.productId = :productId
""")
    void increaseQuantityByProductId(
        @Param("productId") String productId,
        @Param("quantity") Integer quantity
    );
}