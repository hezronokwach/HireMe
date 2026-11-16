package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.EquipmentPhotoService;
import com.example.hireme.equipment.internal.exception.ForbiddenException;
import com.example.hireme.equipment.internal.exception.PhotoNotFoundException;
import com.example.hireme.shared.exception.InvalidRequestException;
import com.example.hireme.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentPhotoServiceImplTest {

    static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D);
    static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1);
    static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');

    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private EquipmentPhotoRepository photoRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private EquipmentPhotoServiceImpl service;

    private final EquipmentEntity listing = new EquipmentEntity();

    @BeforeEach
    void setUp() {
        listing.setId(5L);
        listing.setOwnerId(2L);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    void imageType_readsTheFileItself() {
        assertEquals("image/png", EquipmentPhotoServiceImpl.imageType(PNG));
        assertEquals("image/jpeg", EquipmentPhotoServiceImpl.imageType(JPEG));
        assertEquals("image/webp", EquipmentPhotoServiceImpl.imageType(WEBP));
        assertNull(EquipmentPhotoServiceImpl.imageType("<svg onload=alert(1)>".getBytes()));
        assertNull(EquipmentPhotoServiceImpl.imageType(new byte[3]));
        assertNull(EquipmentPhotoServiceImpl.imageType(null));
    }

    @Test
    void upload_byTheOwner_storesItLastWithTheDetectedType() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(photoRepository.countByEquipmentId(5L)).thenReturn(2L);
        when(photoRepository.findMaxSortOrder(5L)).thenReturn(2);
        when(photoRepository.save(any())).thenAnswer(call -> {
            EquipmentPhotoEntity photo = call.getArgument(0);
            photo.setId(40L);
            return photo;
        });

        EquipmentPhotoService.PhotoResponse response = service.upload(5L, PNG, 2L);

        assertEquals(new EquipmentPhotoService.PhotoResponse(40L, "/api/v1/equipment/5/photos/40"), response);
        ArgumentCaptor<EquipmentPhotoEntity> saved = ArgumentCaptor.forClass(EquipmentPhotoEntity.class);
        verify(photoRepository).save(saved.capture());
        assertEquals("image/png", saved.getValue().getContentType());
        assertEquals(3, saved.getValue().getSortOrder());
        assertEquals(PNG.length, saved.getValue().getSizeBytes());
    }

    @Test
    void upload_somethingThatIsNotAnImage_isRefused() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(listing));

        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.upload(5L, "just text, not a photo".getBytes(), 2L));
        assertEquals("Upload a JPEG, PNG or WebP photo", e.getMessage());
        verify(photoRepository, never()).save(any());
    }

    @Test
    void upload_tooLarge_isRefused() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(listing));
        byte[] big = Arrays.copyOf(PNG, EquipmentPhotoService.MAX_BYTES + 1);

        assertThrows(InvalidRequestException.class, () -> service.upload(5L, big, 2L));
    }

    @Test
    void upload_whenTheListingHasTheMostPhotos_isRefused() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(photoRepository.countByEquipmentId(5L)).thenReturn((long) EquipmentPhotoService.MAX_PHOTOS);

        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> service.upload(5L, JPEG, 2L));
        assertTrue(e.getMessage().contains("at most " + EquipmentPhotoService.MAX_PHOTOS));
    }

    @Test
    void upload_toSomeoneElsesListing_isForbidden() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(userService.isAdmin(9L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.upload(5L, PNG, 9L));
        verify(photoRepository, never()).save(any());
    }

    @Test
    void upload_byAnAdmin_isAllowed() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(listing));
        when(userService.isAdmin(9L)).thenReturn(true);
        when(photoRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertDoesNotThrow(() -> service.upload(5L, PNG, 9L));
    }

    @Test
    void get_aPhotoOfAnotherListing_isNotFound() {
        when(photoRepository.findByIdAndEquipmentId(40L, 6L)).thenReturn(Optional.empty());

        assertThrows(PhotoNotFoundException.class, () -> service.get(6L, 40L));
    }

    @Test
    void makeCover_movesThePhotoInFrontOfTheOthers() {
        when(equipmentRepository.findById(5L)).thenReturn(Optional.of(listing));
        EquipmentPhotoEntity photo = new EquipmentPhotoEntity();
        photo.setSortOrder(4);
        when(photoRepository.findByIdAndEquipmentId(40L, 5L)).thenReturn(Optional.of(photo));
        when(photoRepository.findMinSortOrder(5L)).thenReturn(1);

        service.makeCover(5L, 40L, 2L);

        assertEquals(0, photo.getSortOrder());
        verify(photoRepository).save(photo);
    }

    @Test
    void urlsFor_groupsPhotosByListingInOrder_inOneQuery() {
        when(photoRepository.findRefsByEquipmentIdIn(List.of(5L, 6L, 7L))).thenReturn(List.of(
                ref(5L, 41L), ref(6L, 50L), ref(5L, 40L)));

        Map<Long, List<String>> urls = service.urlsFor(List.of(5L, 6L, 7L));

        assertEquals(List.of("/api/v1/equipment/5/photos/41", "/api/v1/equipment/5/photos/40"), urls.get(5L));
        assertEquals(List.of("/api/v1/equipment/6/photos/50"), urls.get(6L));
        assertFalse(urls.containsKey(7L));
    }

    @Test
    void urlsFor_noListings_skipsTheQuery() {
        assertTrue(service.urlsFor(List.of()).isEmpty());
        verifyNoInteractions(photoRepository);
    }

    @Test
    void photo_comparesTheBytesThemselves_andKeepsThemOutOfItsStringForm() {
        EquipmentPhotoService.Photo a = new EquipmentPhotoService.Photo("image/png", PNG.clone());
        EquipmentPhotoService.Photo b = new EquipmentPhotoService.Photo("image/png", PNG.clone());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new EquipmentPhotoService.Photo("image/png", JPEG));
        assertNotEquals(a, new EquipmentPhotoService.Photo("image/jpeg", PNG));
        assertNotEquals(a, "not a photo");
        assertEquals("Photo[contentType=image/png, bytes=" + PNG.length + "]", a.toString());
        assertEquals("Photo[contentType=null, bytes=0]", new EquipmentPhotoService.Photo(null, null).toString());
    }

    private static EquipmentPhotoRepository.PhotoRef ref(Long equipmentId, Long photoId) {
        return new EquipmentPhotoRepository.PhotoRef() {
            @Override
            public Long getEquipmentId() {
                return equipmentId;
            }

            @Override
            public Long getId() {
                return photoId;
            }
        };
    }
}
