package com.transferservice.transferservice.dto;

import com.transferservice.transferservice.entity.Status;
import com.transferservice.transferservice.entity.Transfer;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:41:35+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class TransferMapperImpl implements TransferMapper {

    @Override
    public Transfer toEntity(TransferRequest request) {
        if ( request == null ) {
            return null;
        }

        Transfer transfer = new Transfer();

        transfer.setIdempotencyKey( request.idempotencyKey() );
        transfer.setTransferAmount( request.transferAmount() );
        transfer.setTransferFrom( request.transferFrom() );
        transfer.setTransferTo( request.transferTo() );

        transfer.setStatus( Status.PENDING );

        return transfer;
    }

    @Override
    public TransferResponse toResponse(Transfer entity) {
        if ( entity == null ) {
            return null;
        }

        Long transferId = null;
        String transferNo = null;
        String transferFrom = null;
        String transferTo = null;
        Integer transferAmount = null;
        LocalDateTime transferTime = null;
        String status = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        transferId = entity.getTransferId();
        transferNo = entity.getTransferNo();
        transferFrom = entity.getTransferFrom();
        transferTo = entity.getTransferTo();
        transferAmount = entity.getTransferAmount();
        transferTime = entity.getTransferTime();
        if ( entity.getStatus() != null ) {
            status = entity.getStatus().name();
        }
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        TransferResponse transferResponse = new TransferResponse( transferId, transferNo, transferFrom, transferTo, transferAmount, transferTime, status, createdAt, updatedAt );

        return transferResponse;
    }
}
