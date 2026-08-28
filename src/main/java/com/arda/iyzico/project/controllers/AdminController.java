package com.arda.iyzico.project.controllers;

import com.arda.iyzico.project.dto.CreateItemRequest;
import com.arda.iyzico.project.dto.ItemResponse;
import com.arda.iyzico.project.services.ItemService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ItemService itemService;

    @GetMapping("/items")
    public List<ItemResponse> getItems() {
        return itemService.getActiveItems();
    }

    @PostMapping("/items")
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.createItem(request));
    }
}
