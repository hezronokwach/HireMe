package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long> {
    List<EquipmentEntity> findByCategory(Category category);
}
