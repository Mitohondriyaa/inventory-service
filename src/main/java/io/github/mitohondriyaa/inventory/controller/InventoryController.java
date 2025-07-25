package io.github.mitohondriyaa.inventory.controller;

import io.github.mitohondriyaa.inventory.dto.InventoryRequest;
import io.github.mitohondriyaa.inventory.dto.InventoryResponse;
import io.github.mitohondriyaa.inventory.model.Inventory;
import io.github.mitohondriyaa.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public boolean isInStock(@RequestParam String productId, @RequestParam Integer quantity) {
        return inventoryService.isInStock(productId, quantity);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> getAllInventories() {
        return inventoryService.getAllInventories();
    }

    @GetMapping("/{productId}")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse getInventoryById(@PathVariable String productId) {
        return inventoryService.getInventoryByProductID(productId);
    }

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse updateInventoryById(@PathVariable String productId, @RequestBody InventoryRequest inventoryRequest) {
        return inventoryService.updateInventoryByProductId(productId, inventoryRequest);
    }
}