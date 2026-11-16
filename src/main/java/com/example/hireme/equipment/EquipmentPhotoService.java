package com.example.hireme.equipment;

import java.util.List;
import java.util.Map;

/** Photos of listings: owners add and remove them; anyone can see them. */
public interface EquipmentPhotoService {

    /** Most photos a listing can have. */
    int MAX_PHOTOS = 6;
    /** Largest photo accepted, in bytes. The frontend shrinks photos well below this before upload. */
    int MAX_BYTES = 2 * 1024 * 1024;

    PhotoResponse upload(Long equipmentId, byte[] data, Long currentUserId);

    Photo get(Long equipmentId, Long photoId);

    void delete(Long equipmentId, Long photoId, Long currentUserId);

    /** Moves a photo to the front, so it becomes the listing's cover. */
    void makeCover(Long equipmentId, Long photoId, Long currentUserId);

    /** Photo URLs of several listings, in display order, in one query. Listings without photos are left out. */
    Map<Long, List<String>> urlsFor(List<Long> equipmentIds);

    record Photo(String contentType, byte[] data) {
    }

    record PhotoResponse(Long id, String url) {
    }

    static String url(Long equipmentId, Long photoId) {
        return "/api/v1/equipment/" + equipmentId + "/photos/" + photoId;
    }
}
