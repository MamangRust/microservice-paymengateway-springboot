package com.topupservice.topupservice.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import com.topupservice.topupservice.entity.Topup;

@Mapper(componentModel = ComponentModel.SPRING)
public interface TopupMapper {
    @Mapping(target = "topupId", ignore = true)
    @Mapping(target = "topupNo", ignore = true)
    @Mapping(target = "topupTime", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Topup toEntity(TopupRequest request);
    TopupResponse toResponse(Topup entity);
}