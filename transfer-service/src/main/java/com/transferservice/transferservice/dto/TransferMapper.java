package com.transferservice.transferservice.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import com.transferservice.transferservice.entity.Transfer;

@Mapper(componentModel = ComponentModel.SPRING)
public interface TransferMapper {
    @Mapping(target = "transferId", ignore = true)
    @Mapping(target = "transferNo", ignore = true)
    @Mapping(target = "transferTime", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Transfer toEntity(TransferRequest request);
    TransferResponse toResponse(Transfer entity);
}