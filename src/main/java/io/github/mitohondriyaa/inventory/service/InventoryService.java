package io.github.mitohondriyaa.inventory.service;

import io.github.mitohondriyaa.inventory.dto.InventoryRequest;
import io.github.mitohondriyaa.inventory.dto.InventoryResponse;
import io.github.mitohondriyaa.inventory.exception.NotFoundException;
import io.github.mitohondriyaa.inventory.model.Inventory;
import io.github.mitohondriyaa.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    public boolean isInStock(String skuCode, Integer quantity) {
        return inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode, quantity);
    }

    public InventoryResponse createInventory(InventoryRequest inventoryRequest) {
        Inventory inventory = Inventory.builder()
            .skuCode(inventoryRequest.skuCode())
            .quantity(inventoryRequest.quantity())
            .build();

        inventory = inventoryRepository.save(inventory);

        return new InventoryResponse(
            inventory.getId(),
            inventory.getSkuCode(),
            inventory.getQuantity()
        );
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

    public void deleteInventoryById(Long id) {
        Inventory inventory = inventoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory not found"));

        inventoryRepository.delete(inventory);
    }
}