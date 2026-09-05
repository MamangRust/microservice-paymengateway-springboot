package com.saldoservice.saldoservice.dto;

import com.saldoservice.saldoservice.entity.Saldo;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:41:30+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class SaldoMapperImpl implements SaldoMapper {

    @Override
    public Saldo toEntity(SaldoRequest request) {
        if ( request == null ) {
            return null;
        }

        Saldo saldo = new Saldo();

        saldo.setCardNumber( request.cardNumber() );
        saldo.setTotalBalance( request.totalBalance() );

        return saldo;
    }

    @Override
    public SaldoResponse toResponse(Saldo entity) {
        if ( entity == null ) {
            return null;
        }

        Long saldoId = null;
        String cardNumber = null;
        Integer totalBalance = null;
        Integer withdrawAmount = null;
        LocalDateTime withdrawTime = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        saldoId = entity.getSaldoId();
        cardNumber = entity.getCardNumber();
        totalBalance = entity.getTotalBalance();
        withdrawAmount = entity.getWithdrawAmount();
        withdrawTime = entity.getWithdrawTime();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        SaldoResponse saldoResponse = new SaldoResponse( saldoId, cardNumber, totalBalance, withdrawAmount, withdrawTime, createdAt, updatedAt );

        return saldoResponse;
    }
}
