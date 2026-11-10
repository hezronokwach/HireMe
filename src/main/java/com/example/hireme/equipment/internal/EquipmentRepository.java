package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long> {
    List<EquipmentEntity> findByCategory(Category category);

    @Query("select e.id from EquipmentEntity e where e.ownerId = :ownerId")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);
}
