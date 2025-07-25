package io.github.mitohondriyaa.inventory.service;

import io.github.mitohondriyaa.inventory.dto.InventoryRequest;
import io.github.mitohondriyaa.inventory.dto.InventoryResponse;
import io.github.mitohondriyaa.inventory.exception.NotFoundException;
import io.github.mitohondriyaa.inventory.model.Inventory;
import io.github.mitohondriyaa.inventory.repository.InventoryRepository;
import io.github.mitohondriyaa.product.event.ProductCreatedEvent;
import io.github.mitohondriyaa.product.event.ProductDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    @KafkaListener(topics = "product-created")
    public void createInventory(ProductCreatedEvent productCreatedEvent) {
        Inventory inventory = Inventory.builder()
            .productId(productCreatedEvent.getProductId().toString())
            .quantity(0)
            .build();

        inventoryRepository.save(inventory);
    }

    public List<InventoryResponse> getAllInventories() {
        return inventoryRepository.findAll()
            .stream()
            .map(inventory -> new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getQuantity()))
            .toList();
    }

    public boolean isInStock(String productId, Integer quantity) {
        return inventoryRepository.existsByProductIdAndQuantityIsGreaterThanEqual(productId, quantity);
    }

    public InventoryResponse getInventoryByProductID(String productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException("Inventory not found"));

        return new InventoryResponse(
            inventory.getId(),
            inventory.getProductId(),
            inventory.getQuantity()
        );
    }

    public InventoryResponse updateInventoryByProductId(String productId, InventoryRequest inventoryRequest) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException("Inventory not found"));

        inventory.setProductId(inventoryRequest.productId());
        inventory.setQuantity(inventoryRequest.quantity());

        inventoryRepository.save(inventory);

        return new InventoryResponse(
            inventory.getId(),
            inventory.getProductId(),
            inventory.getQuantity()
        );
    }

    @KafkaListener(topics = "product-deleted")
    public void deleteInventoryByProductID(ProductDeletedEvent productDeletedEvent) {
        inventoryRepository.deleteByProductId(productDeletedEvent.getProductId().toString());
    }
}