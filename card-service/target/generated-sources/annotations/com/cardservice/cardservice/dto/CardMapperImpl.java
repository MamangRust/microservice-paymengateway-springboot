package com.cardservice.cardservice.dto;

import com.cardservice.cardservice.entity.Card;
import com.cardservice.cardservice.entity.CardStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:41:23+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class CardMapperImpl implements CardMapper {

    @Override
    public Card toEntity(CardRequest request) {
        if ( request == null ) {
            return null;
        }

        Card card = new Card();

        card.setCardNumber( request.cardNumber() );
        card.setCardProvider( request.cardProvider() );
        card.setCardType( request.cardType() );
        card.setCreditLimit( request.creditLimit() );
        card.setCvv( request.cvv() );
        card.setExpireDate( request.expireDate() );
        card.setPoints( request.points() );
        card.setUserId( request.userId() );

        card.setStatus( CardStatus.ACTIVE );

        return card;
    }

    @Override
    public CardResponse toResponse(Card entity) {
        if ( entity == null ) {
            return null;
        }

        Long cardId = null;
        Integer userId = null;
        String cardNumber = null;
        String cardType = null;
        LocalDate expireDate = null;
        String cardProvider = null;
        String status = null;
        BigDecimal creditLimit = null;
        BigDecimal points = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        cardId = entity.getCardId();
        userId = entity.getUserId();
        cardNumber = entity.getCardNumber();
        cardType = entity.getCardType();
        expireDate = entity.getExpireDate();
        cardProvider = entity.getCardProvider();
        if ( entity.getStatus() != null ) {
            status = entity.getStatus().name();
        }
        creditLimit = entity.getCreditLimit();
        points = entity.getPoints();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        CardResponse cardResponse = new CardResponse( cardId, userId, cardNumber, cardType, expireDate, cardProvider, status, creditLimit, points, createdAt, updatedAt );

        return cardResponse;
    }
}
