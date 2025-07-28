package io.github.mitohondriyaa.inventory.service;

import io.github.mitohondriyaa.inventory.dto.InventoryRequest;
import io.github.mitohondriyaa.inventory.dto.InventoryResponse;
import io.github.mitohondriyaa.inventory.event.InventoryRejectedEvent;
import io.github.mitohondriyaa.inventory.event.InventoryReservedEvent;
import io.github.mitohondriyaa.inventory.exception.NotEnoughInventoryException;
import io.github.mitohondriyaa.inventory.exception.NotFoundException;
import io.github.mitohondriyaa.inventory.model.Inventory;
import io.github.mitohondriyaa.inventory.repository.InventoryRepository;
import io.github.mitohondriyaa.order.event.OrderCancelledEvent;
import io.github.mitohondriyaa.order.event.OrderPlacedEvent;
import io.github.mitohondriyaa.product.event.ProductCreatedEvent;
import io.github.mitohondriyaa.product.event.ProductDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "product-created")
    public void createInventory(ProductCreatedEvent productCreatedEvent) {
        Inventory inventory = Inventory.builder()
            .productId(productCreatedEvent.getProductId().toString())
            .quantity(0)
            .build();

        inventoryRepository.save(inventory);
    }

    @KafkaListener(topics = "order-placed")
    public void deductStock(OrderPlacedEvent orderPlacedEvent) {
        Integer updated = inventoryRepository.decreaseQuantityIfEnough(
            orderPlacedEvent.getProductId().toString(),
            orderPlacedEvent.getQuantity()
        );

        if (updated == 0) {
            InventoryRejectedEvent inventoryRejectedEvent
                = new InventoryRejectedEvent();
            inventoryRejectedEvent.setOrderNumber(
                orderPlacedEvent.getOrderNumber()
            );
            inventoryRejectedEvent.setEmail(
                orderPlacedEvent.getEmail()
            );
            inventoryRejectedEvent.setFirstName(
                orderPlacedEvent.getFirstName()
            );
            inventoryRejectedEvent.setLastName(
                orderPlacedEvent.getLastName()
            );

            kafkaTemplate.send("inventory-rejected", inventoryRejectedEvent);
        } else {
            InventoryReservedEvent inventoryReservedEvent
                = new InventoryReservedEvent();
            inventoryReservedEvent.setOrderNumber(
                orderPlacedEvent.getOrderNumber()
            );
            inventoryReservedEvent.setEmail(
                orderPlacedEvent.getEmail()
            );
            inventoryReservedEvent.setFirstName(
                orderPlacedEvent.getFirstName()
            );
            inventoryReservedEvent.setLastName(
                orderPlacedEvent.getLastName()
            );

            kafkaTemplate.sendDefault(inventoryReservedEvent);
        }
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

    public InventoryResponse updateInventoryByProductId(InventoryRequest inventoryRequest) {
        Inventory inventory = inventoryRepository.findByProductId(inventoryRequest.productId())
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

    @KafkaListener(topics = "order-cancelled")
    public void orderCancelled(OrderCancelledEvent orderCancelledEvent) {
        inventoryRepository.increaseQuantityByProductId(
            orderCancelledEvent.getProductId().toString(),
            orderCancelledEvent.getQuantity()
        );
    }
}