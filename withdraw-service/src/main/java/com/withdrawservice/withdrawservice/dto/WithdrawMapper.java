package com.withdrawservice.withdrawservice.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import com.withdrawservice.withdrawservice.entity.Withdraw;

@Mapper(componentModel = ComponentModel.SPRING)
public interface WithdrawMapper {
    @Mapping(target = "withdrawId", ignore = true)
    @Mapping(target = "withdrawNo", ignore = true)
    @Mapping(target = "withdrawTime", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Withdraw toEntity(WithdrawRequest request);
    WithdrawResponse toResponse(Withdraw entity);
}