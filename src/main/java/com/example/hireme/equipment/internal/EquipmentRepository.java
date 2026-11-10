package com.example.hireme.equipment.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long>, JpaSpecificationExecutor<EquipmentEntity> {

    @Query("select e.id from EquipmentEntity e where e.ownerId = :ownerId")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EquipmentEntity e where e.id = :id")
    Optional<EquipmentEntity> findByIdForUpdate(@Param("id") Long id);
}
