package com.example.hireme.equipment;

import com.example.hireme.shared.exception.InvalidRequestException;
import com.example.hireme.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/photos")
@RequiredArgsConstructor
@Tag(name = "Equipment")
public class EquipmentPhotoController {

    private final EquipmentPhotoService photoService;
    private final UserService userService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "uploadEquipmentPhoto", summary = "Add a photo to a listing you own (OWNER, ADMIN)",
            description = "Multipart field `file`: a JPEG, PNG or WebP photo, at most 2 MB. A listing can have "
                    + "up to " + EquipmentPhotoService.MAX_PHOTOS + " photos; the first one is its cover.")
    @ApiResponse(responseCode = "201", description = "Photo added")
    @ApiResponse(responseCode = "400", description = "Not an image, too large, or the listing already has the most photos")
    @ApiResponse(responseCode = "403", description = "Not your listing")
    public ResponseEntity<EquipmentPhotoService.PhotoResponse> upload(
            @PathVariable Long equipmentId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User userDetails
    ) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidRequestException("Choose a photo to upload");
        }
        return new ResponseEntity<>(photoService.upload(equipmentId, file.getBytes(), getUserId(userDetails)),
                HttpStatus.CREATED);
    }

    @GetMapping("/{photoId}")
    @SecurityRequirements // no token needed
    @Operation(operationId = "getEquipmentPhoto", summary = "A listing's photo (the image itself; no token needed)")
    @ApiResponse(responseCode = "200", description = "The image")
    @ApiResponse(responseCode = "404", description = "No such photo on this listing")
    public ResponseEntity<byte[]> get(@PathVariable Long equipmentId, @PathVariable Long photoId) {
        EquipmentPhotoService.Photo photo = photoService.get(equipmentId, photoId);
        // A photo id always means the same picture (a new photo gets a new id), so browsers may keep it.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(photo.data());
    }

    @DeleteMapping("/{photoId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "deleteEquipmentPhoto", summary = "Remove a photo from a listing you own (OWNER, ADMIN)")
    @ApiResponse(responseCode = "204", description = "Removed")
    public ResponseEntity<Void> delete(
            @PathVariable Long equipmentId,
            @PathVariable Long photoId,
            @AuthenticationPrincipal User userDetails
    ) {
        photoService.delete(equipmentId, photoId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{photoId}/cover")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "makeEquipmentPhotoCover", summary = "Make a photo the listing's cover (OWNER, ADMIN)")
    @ApiResponse(responseCode = "204", description = "It's now the first photo")
    public ResponseEntity<Void> makeCover(
            @PathVariable Long equipmentId,
            @PathVariable Long photoId,
            @AuthenticationPrincipal User userDetails
    ) {
        photoService.makeCover(equipmentId, photoId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(User userDetails) {
        return userService.getUserIdByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found in security context"));
    }
}
