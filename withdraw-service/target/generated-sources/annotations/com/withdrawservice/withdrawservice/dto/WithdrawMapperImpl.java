package com.withdrawservice.withdrawservice.dto;

import com.withdrawservice.withdrawservice.entity.Status;
import com.withdrawservice.withdrawservice.entity.Withdraw;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:41:37+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class WithdrawMapperImpl implements WithdrawMapper {

    @Override
    public Withdraw toEntity(WithdrawRequest request) {
        if ( request == null ) {
            return null;
        }

        Withdraw withdraw = new Withdraw();

        withdraw.setCardNumber( request.cardNumber() );
        withdraw.setIdempotencyKey( request.idempotencyKey() );
        withdraw.setWithdrawAmount( request.withdrawAmount() );

        withdraw.setStatus( Status.PENDING );

        return withdraw;
    }

    @Override
    public WithdrawResponse toResponse(Withdraw entity) {
        if ( entity == null ) {
            return null;
        }

        Long withdrawId = null;
        String withdrawNo = null;
        String cardNumber = null;
        Integer withdrawAmount = null;
        LocalDateTime withdrawTime = null;
        String status = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        withdrawId = entity.getWithdrawId();
        withdrawNo = entity.getWithdrawNo();
        cardNumber = entity.getCardNumber();
        withdrawAmount = entity.getWithdrawAmount();
        withdrawTime = entity.getWithdrawTime();
        if ( entity.getStatus() != null ) {
            status = entity.getStatus().name();
        }
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        WithdrawResponse withdrawResponse = new WithdrawResponse( withdrawId, withdrawNo, cardNumber, withdrawAmount, withdrawTime, status, createdAt, updatedAt );

        return withdrawResponse;
    }
}
