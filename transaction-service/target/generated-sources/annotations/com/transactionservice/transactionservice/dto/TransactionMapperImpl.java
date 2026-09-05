package com.transactionservice.transactionservice.dto;

import com.transactionservice.transactionservice.entity.Status;
import com.transactionservice.transactionservice.entity.Transaction;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:41:34+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class TransactionMapperImpl implements TransactionMapper {

    @Override
    public Transaction toEntity(TransactionRequest request) {
        if ( request == null ) {
            return null;
        }

        Transaction transaction = new Transaction();

        transaction.setAmount( request.amount() );
        transaction.setCardNumber( request.cardNumber() );
        transaction.setIdempotencyKey( request.idempotencyKey() );
        transaction.setMerchantId( request.merchantId() );
        transaction.setPaymentMethod( request.paymentMethod() );

        transaction.setStatus( Status.PENDING );

        return transaction;
    }

    @Override
    public TransactionResponse toResponse(Transaction entity) {
        if ( entity == null ) {
            return null;
        }

        Long transactionId = null;
        String transactionNo = null;
        String cardNumber = null;
        Integer amount = null;
        String paymentMethod = null;
        Integer merchantId = null;
        LocalDateTime transactionTime = null;
        String status = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        transactionId = entity.getTransactionId();
        transactionNo = entity.getTransactionNo();
        cardNumber = entity.getCardNumber();
        amount = entity.getAmount();
        paymentMethod = entity.getPaymentMethod();
        merchantId = entity.getMerchantId();
        transactionTime = entity.getTransactionTime();
        if ( entity.getStatus() != null ) {
            status = entity.getStatus().name();
        }
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        TransactionResponse transactionResponse = new TransactionResponse( transactionId, transactionNo, cardNumber, amount, paymentMethod, merchantId, transactionTime, status, createdAt, updatedAt );

        return transactionResponse;
    }
}
