package com.transactionservice.transactionservice.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import com.transactionservice.transactionservice.entity.Transaction;

@Mapper(componentModel = ComponentModel.SPRING)
public interface TransactionMapper {
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "transactionNo", ignore = true)
    @Mapping(target = "transactionTime", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Transaction toEntity(TransactionRequest request);
    TransactionResponse toResponse(Transaction entity);
}