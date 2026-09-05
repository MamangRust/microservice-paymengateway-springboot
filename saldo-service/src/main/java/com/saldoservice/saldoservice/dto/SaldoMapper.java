package com.saldoservice.saldoservice.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import com.saldoservice.saldoservice.entity.Saldo;

@Mapper(componentModel = ComponentModel.SPRING)
public interface SaldoMapper {
    @Mapping(target = "saldoId", ignore = true)
    @Mapping(target = "withdrawAmount", ignore = true)
    @Mapping(target = "withdrawTime", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Saldo toEntity(SaldoRequest request);
    SaldoResponse toResponse(Saldo entity);
}