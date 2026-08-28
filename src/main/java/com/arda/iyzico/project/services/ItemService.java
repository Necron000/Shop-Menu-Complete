package com.arda.iyzico.project.services;

import com.arda.iyzico.project.dto.CreateItemRequest;
import com.arda.iyzico.project.dto.ItemResponse;
import com.arda.iyzico.project.models.Item;
import java.util.List;

public interface ItemService {
    List<ItemResponse> getActiveItems();
    ItemResponse createItem(CreateItemRequest request);
    ItemResponse getItemById(Long id);
    Item getItemForPurchase(Long itemId);

}

