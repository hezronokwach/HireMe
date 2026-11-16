package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.EquipmentPhotoService;
import com.example.hireme.equipment.internal.exception.EquipmentNotFoundException;
import com.example.hireme.equipment.internal.exception.ForbiddenException;
import com.example.hireme.equipment.internal.exception.PhotoNotFoundException;
import com.example.hireme.shared.exception.InvalidRequestException;
import com.example.hireme.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentPhotoServiceImpl implements EquipmentPhotoService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentPhotoRepository photoRepository;
    private final UserService userService;

    @Override
    @Transactional
    public PhotoResponse upload(Long equipmentId, byte[] data, Long currentUserId) {
        checkCanManage(equipmentId, currentUserId);
        // The type comes from the file's own bytes; whatever the upload claims isn't trusted.
        String type = imageType(data);
        if (type == null) {
            throw new InvalidRequestException("Upload a JPEG, PNG or WebP photo");
        }
        if (data.length > MAX_BYTES) {
            throw new InvalidRequestException("A photo can be at most " + MAX_BYTES / (1024 * 1024) + " MB");
        }
        if (photoRepository.countByEquipmentId(equipmentId) >= MAX_PHOTOS) {
            throw new InvalidRequestException("A listing can have at most " + MAX_PHOTOS + " photos");
        }
        EquipmentPhotoEntity photo = new EquipmentPhotoEntity();
        photo.setEquipmentId(equipmentId);
        photo.setContentType(type);
        photo.setImageData(data);
        photo.setSizeBytes(data.length);
        photo.setSortOrder(photoRepository.findMaxSortOrder(equipmentId) + 1);
        photo.setCreatedAt(Instant.now());
        EquipmentPhotoEntity saved = photoRepository.save(photo);
        return new PhotoResponse(saved.getId(), EquipmentPhotoService.url(equipmentId, saved.getId()));
    }

    @Override
    public Photo get(Long equipmentId, Long photoId) {
        EquipmentPhotoEntity photo = photoRepository.findByIdAndEquipmentId(photoId, equipmentId)
                .orElseThrow(PhotoNotFoundException::new);
        return new Photo(photo.getContentType(), photo.getImageData());
    }

    @Override
    @Transactional
    public void delete(Long equipmentId, Long photoId, Long currentUserId) {
        checkCanManage(equipmentId, currentUserId);
        photoRepository.delete(photoRepository.findByIdAndEquipmentId(photoId, equipmentId)
                .orElseThrow(PhotoNotFoundException::new));
    }

    @Override
    @Transactional
    public void makeCover(Long equipmentId, Long photoId, Long currentUserId) {
        checkCanManage(equipmentId, currentUserId);
        EquipmentPhotoEntity photo = photoRepository.findByIdAndEquipmentId(photoId, equipmentId)
                .orElseThrow(PhotoNotFoundException::new);
        photo.setSortOrder(photoRepository.findMinSortOrder(equipmentId) - 1);
        photoRepository.save(photo);
    }

    @Override
    public Map<Long, List<String>> urlsFor(List<Long> equipmentIds) {
        Map<Long, List<String>> urls = new LinkedHashMap<>();
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return urls;
        }
        for (EquipmentPhotoRepository.PhotoRef ref : photoRepository.findRefsByEquipmentIdIn(equipmentIds)) {
            urls.computeIfAbsent(ref.getEquipmentId(), id -> new ArrayList<>())
                    .add(EquipmentPhotoService.url(ref.getEquipmentId(), ref.getId()));
        }
        return urls;
    }

    private void checkCanManage(Long equipmentId, Long currentUserId) {
        EquipmentEntity equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        if (!Objects.equals(equipment.getOwnerId(), currentUserId) && !userService.isAdmin(currentUserId)) {
            throw new ForbiddenException();
        }
    }

    /** The image type from the file's first bytes (its "magic number"), or null when it isn't one we accept. */
    static String imageType(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
