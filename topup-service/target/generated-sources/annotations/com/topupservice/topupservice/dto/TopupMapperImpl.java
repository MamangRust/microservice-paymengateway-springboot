package com.topupservice.topupservice.dto;

import com.topupservice.topupservice.entity.Status;
import com.topupservice.topupservice.entity.Topup;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:41:33+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class TopupMapperImpl implements TopupMapper {

    @Override
    public Topup toEntity(TopupRequest request) {
        if ( request == null ) {
            return null;
        }

        Topup topup = new Topup();

        topup.setCardNumber( request.cardNumber() );
        topup.setIdempotencyKey( request.idempotencyKey() );
        topup.setTopupAmount( request.topupAmount() );
        topup.setTopupMethod( request.topupMethod() );

        topup.setStatus( Status.PENDING );

        return topup;
    }

    @Override
    public TopupResponse toResponse(Topup entity) {
        if ( entity == null ) {
            return null;
        }

        Long topupId = null;
        String topupNo = null;
        String cardNumber = null;
        Integer topupAmount = null;
        String topupMethod = null;
        LocalDateTime topupTime = null;
        String status = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        topupId = entity.getTopupId();
        topupNo = entity.getTopupNo();
        cardNumber = entity.getCardNumber();
        topupAmount = entity.getTopupAmount();
        topupMethod = entity.getTopupMethod();
        topupTime = entity.getTopupTime();
        if ( entity.getStatus() != null ) {
            status = entity.getStatus().name();
        }
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        TopupResponse topupResponse = new TopupResponse( topupId, topupNo, cardNumber, topupAmount, topupMethod, topupTime, status, createdAt, updatedAt );

        return topupResponse;
    }
}
