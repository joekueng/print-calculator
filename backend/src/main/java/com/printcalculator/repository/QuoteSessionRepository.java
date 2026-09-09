package com.printcalculator.repository;

import com.printcalculator.entity.QuoteSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteSessionRepository extends JpaRepository<QuoteSession, UUID> {
    List<QuoteSession> findByExpiresAtBefore(java.time.OffsetDateTime cutoff);

    List<QuoteSession> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    Optional<QuoteSession> findByIdAndSessionType(UUID id, String sessionType);

    @Query("select count(s) from QuoteSession s")
    long countAllForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where exists (
                select i.id from QuoteLineItem i where i.quoteSession = s
            )
            """)
    long countWithItemsForStatistics();

    @Query("select count(i) from QuoteLineItem i")
    long countLineItemsForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where exists (
                select o.id from Order o where o.sourceQuoteSession = s
            )
            """)
    long countConvertedForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where exists (
                select i.id from QuoteLineItem i where i.quoteSession = s
            )
              and exists (
                select o.id from Order o
                where o.sourceQuoteSession = s
                  and o.status <> 'CANCELLED'
                  and exists (
                      select p.id from Payment p
                      where p.order = o and p.status in ('RECEIVED', 'COMPLETED')
                  )
            )
            """)
    long countPaidConvertedForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where exists (
                select i.id from QuoteLineItem i
                where i.quoteSession = s and i.updatedAt > i.createdAt
            )
            """)
    long countModifiedForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where s.expiresAt < :now
              and exists (
                  select i.id from QuoteLineItem i where i.quoteSession = s
              )
              and not exists (
                  select o.id from Order o
                  where o.sourceQuoteSession = s
                    and o.status <> 'CANCELLED'
                    and exists (
                        select p.id from Payment p
                        where p.order = o and p.status in ('RECEIVED', 'COMPLETED')
                    )
              )
            """)
    long countExpiredWithoutPaidConversionForStatistics(@Param("now") OffsetDateTime now);
}
