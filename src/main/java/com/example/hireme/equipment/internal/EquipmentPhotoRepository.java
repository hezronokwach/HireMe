package com.example.hireme.equipment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EquipmentPhotoRepository extends JpaRepository<EquipmentPhotoEntity, Long> {

    /** Which photos a listing has, in display order, without loading the pictures. */
    interface PhotoRef {
        Long getEquipmentId();

        Long getId();
    }

    @Query("select p.equipmentId as equipmentId, p.id as id from EquipmentPhotoEntity p "
            + "where p.equipmentId in :equipmentIds order by p.sortOrder, p.id")
    List<PhotoRef> findRefsByEquipmentIdIn(@Param("equipmentIds") Collection<Long> equipmentIds);

    long countByEquipmentId(Long equipmentId);

    @Query("select coalesce(max(p.sortOrder), 0) from EquipmentPhotoEntity p where p.equipmentId = :equipmentId")
    int findMaxSortOrder(@Param("equipmentId") Long equipmentId);

    @Query("select coalesce(min(p.sortOrder), 0) from EquipmentPhotoEntity p where p.equipmentId = :equipmentId")
    int findMinSortOrder(@Param("equipmentId") Long equipmentId);

    Optional<EquipmentPhotoEntity> findByIdAndEquipmentId(Long id, Long equipmentId);
}
