package io.github.mitohondriyaa.inventory.service;

import io.github.mitohondriyaa.inventory.dto.InventoryRequest;
import io.github.mitohondriyaa.inventory.dto.InventoryResponse;
import io.github.mitohondriyaa.inventory.exception.NotFoundException;
import io.github.mitohondriyaa.inventory.model.Inventory;
import io.github.mitohondriyaa.inventory.repository.InventoryRepository;
import io.github.mitohondriyaa.product.event.ProductCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    @KafkaListener(topics = "product-created")
    public void createInventory(ProductCreatedEvent productCreatedEvent) {
        Inventory inventory = Inventory.builder()
            .skuCode(productCreatedEvent.getSkuCode().toString())
            .quantity(0)
            .build();

        inventoryRepository.save(inventory);
    }

    public List<InventoryResponse> getAllInventories() {
        return inventoryRepository.findAll()
            .stream()
            .map(inventory -> new InventoryResponse(inventory.getId(), inventory.getSkuCode(), inventory.getQuantity()))
            .toList();
    }

    public boolean isInStock(String skuCode, Integer quantity) {
        return inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode, quantity);
    }

    public InventoryResponse getInventoryById(Long id) {
        Inventory inventory = inventoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory not found"));

        return new InventoryResponse(
            inventory.getId(),
            inventory.getSkuCode(),
            inventory.getQuantity()
        );
    }

    public InventoryResponse updateInventoryById(Long id, InventoryRequest inventoryRequest) {
        Inventory inventory = inventoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory not found"));

        inventory.setSkuCode(inventoryRequest.skuCode());
        inventory.setQuantity(inventoryRequest.quantity());

        inventoryRepository.save(inventory);

        return new InventoryResponse(
            inventory.getId(),
            inventory.getSkuCode(),
            inventory.getQuantity()
        );
    }
}