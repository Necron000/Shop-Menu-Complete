package com.arda.iyzico.project.services;

import com.arda.iyzico.project.dto.CreateItemRequest;
import com.arda.iyzico.project.dto.ItemResponse;
import com.arda.iyzico.project.models.Item;
import com.arda.iyzico.project.repositories.ItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultItemService implements ItemService {

    private final ItemRepository itemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ItemResponse> getActiveItems() {
        return itemRepository.findByActiveTrue().stream()
            .map(ItemResponse::from)
            .toList();
    }

    @Override
    @Transactional
    public ItemResponse createItem(CreateItemRequest request) {
        Item item = Item.builder()
            .name(request.getName())
            .description(request.getDescription())
            .price(request.getPrice())
            .currency(request.getCurrency())
            .stock(request.getStock())
            .active(true)
            .build();

        return ItemResponse.from(itemRepository.save(item));
    }

    @Override
    @Transactional(readOnly = true)
    public ItemResponse getItemById(Long id) {
        return itemRepository.findById(id)
                .map(ItemResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Item getItemForPurchase(Long itemId) {
        return itemRepository.findByIdAndActiveTrue(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found or inactive."));
    }
}
