package com.printcalculator.repository;

import com.printcalculator.entity.OrderDeliverableFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderDeliverableFileRepository extends JpaRepository<OrderDeliverableFile, UUID> {
    List<OrderDeliverableFile> findByOrder_IdOrderByCreatedAtAsc(UUID orderId);

    long countByOrder_Id(UUID orderId);
}
