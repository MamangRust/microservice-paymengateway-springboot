package com.cardservice.cardservice.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import com.cardservice.cardservice.entity.Card;

@Mapper(componentModel = ComponentModel.SPRING)
public interface CardMapper {
    @Mapping(target = "cardId", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Card toEntity(CardRequest request);
    CardResponse toResponse(Card entity);
}