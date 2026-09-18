package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.billing.BillingService;
import com.digitalnest.petmemorial.billing.FeatureCode;
import com.digitalnest.petmemorial.media.MediaAsset;
import com.digitalnest.petmemorial.shared.error.ApiException;
import com.digitalnest.petmemorial.media.MediaService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemorialServiceTest {

    @Mock
    private MemorialRepository memorialRepository;

    @Mock
    private MediaService mediaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BillingService billingService;

    @Mock
    private MemorialShareRepository memorialShareRepository;

    @InjectMocks
    private MemorialService memorialService;

    @Test
    void privateMemorialCannotBePublished() {
        UUID memorialId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Memorial draft = new Memorial(
                memorialId, userId, "pet-12345678", "豆包", "小狗", null, null, "想你", null, null, null, "DRAFT", "PRIVATE", "SUNNY", null,
                null, OffsetDateTime.now(), OffsetDateTime.now(), 0);
        when(memorialRepository.findByIdAndUserId(memorialId, userId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> memorialService.publish(memorialId, userId))
                .isInstanceOf(ApiException.class)
                .hasMessage("发布前，请选择公开、链接访问或访问口令。");
    }

    @Test
    void cannotAddPhotoPastTheEffectivePlanCapacity() {
        UUID memorialId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Memorial draft = new Memorial(
                memorialId, userId, "pet-12345678", "豆包", "小狗", null, null, "想你", null, null, null, "DRAFT", "PRIVATE", "SUNNY", null,
                null, OffsetDateTime.now(), OffsetDateTime.now(), 0);
        UUID mediaId = UUID.randomUUID();
        MediaAsset photo = new MediaAsset(mediaId, userId, "photo.png", "image/png", 4, OffsetDateTime.now());
        when(memorialRepository.findByIdAndUserId(memorialId, userId)).thenReturn(Optional.of(draft));
        when(mediaService.getForOwner(mediaId, userId)).thenReturn(photo);
        when(memorialRepository.hasGalleryMedia(memorialId, mediaId)).thenReturn(false);
        when(memorialRepository.countGalleryItemsByContentTypePrefix(memorialId, "image/")).thenReturn(1);
        when(billingService.limitsFor(userId)).thenReturn(new BillingService.AccountLimits(1, 0, 5));

        assertThatThrownBy(() -> memorialService.addGalleryItem(memorialId, userId, mediaId, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("当前套餐的照片数量已满，请整理已有内容或升级套餐。");
    }

    @Test
    void companionEndDateCannotPrecedeTheStartDate() {
        UUID userId = UUID.randomUUID();
        MemorialRepository.CreateMemorialCommand command = new MemorialRepository.CreateMemorialCommand(
                "豆包", "小狗", null, null, null,
                java.time.LocalDate.of(2025, 2, 1), java.time.LocalDate.of(2025, 1, 1),
                "LINK", "SUNNY", null);

        assertThatThrownBy(() -> memorialService.create(userId, command))
                .isInstanceOf(ApiException.class)
                .hasMessage("陪伴结束日期不能早于开始日期。");
    }

    @Test
    void premiumAppearanceCannotBeActivatedByAFreeAccount() {
        UUID userId = UUID.randomUUID();
        MemorialRepository.CreateMemorialCommand command = new MemorialRepository.CreateMemorialCommand(
                "豆包", "小狗", null, null, null, null, null,
                "LINK", "SUNNY", null);
        when(billingService.hasCapability(userId, FeatureCode.PREMIUM_APPEARANCE_PACK))
                .thenReturn(false);

        assertThatThrownBy(() -> memorialService.create(userId, command))
                .isInstanceOf(ApiException.class)
                .hasMessage("这个外观包需要开通后使用；已有纪念内容不会受到影响。");
    }
}
