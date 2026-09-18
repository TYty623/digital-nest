package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.media.MediaAsset;
import com.digitalnest.petmemorial.media.MediaService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MemorialExperienceIntegrationTest {
    @Autowired MemorialExperienceService service;
    @Autowired MemorialService memorialService;
    @Autowired MediaService mediaService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ownerCanBuildRoomWhileBurialStaysPrivateUntilReviewed() {
        UUID owner = createUser("小满的家人");
        UUID reviewer = createUser("审核员");
        UUID memorial = UUID.randomUUID();
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility) VALUES (?,?,?,?,?,'PUBLISHED','PUBLIC')",
                memorial, owner, "room-" + UUID.randomUUID(), "小满", "小狗");

        MediaAsset audio = mediaService.uploadAudio(owner, new MockMultipartFile("file", "bell.mp3", "audio/mpeg",
                new byte[]{0x49,0x44,0x33,0x04,0,0,0,0,0,0,0,0}));
        service.addSound(memorial, owner, audio.id(), "小铃铛", "每次回家都能听见");
        service.saveInterview(memorial, owner, "HABIT", "睡着时会轻轻打呼。");
        service.addKeepsake(memorial, owner, "红色项圈", "陪 TA 去过很多地方。");
        service.saveBurial(memorial, owner, "CREMATION", null, "上海市", "家中的纪念角", "窗边一直有阳光。");

        MemorialExperience beforeReview = service.publicExperience(memorial);
        assertEquals(1, beforeReview.sounds().size());
        assertEquals(1, beforeReview.interviewAnswers().size());
        assertEquals(1, beforeReview.keepsakes().size());
        assertNull(beforeReview.burial());

        MemorialExperience.BurialRecord pending = service.submitBurial(memorial, owner, true);
        assertEquals("PENDING", pending.reviewStatus());
        assertNull(service.publicExperience(memorial).burial());
        service.moderate(pending.id(), "APPROVED", "仅含城市级信息，符合公开规则", reviewer);
        assertEquals("APPROVED", service.publicExperience(memorial).burial().reviewStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE target_id=?", Integer.class, pending.id()));
    }

    @Test
    void burialRejectsExactAddressAndTransactionContent() {
        UUID owner = createUser("隐私测试主人");
        UUID memorial = UUID.randomUUID();
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility) VALUES (?,?,?,?,?,'DRAFT','PRIVATE')",
                memorial, owner, "privacy-" + UUID.randomUUID(), "团团", "小猫");
        ApiException address = assertThrows(ApiException.class, () -> service.saveBurial(memorial, owner, "CREMATION", null,
                "上海市", "幸福路88号", null));
        assertEquals("BURIAL_SENSITIVE_INFORMATION", address.code());
        ApiException transaction = assertThrows(ApiException.class, () -> service.saveBurial(memorial, owner, "OTHER_LAWFUL", null,
                "上海市", null, "提供遗体交易报价"));
        assertEquals("BURIAL_TRANSACTION_CONTENT", transaction.code());
    }

    @Test
    void ownerCanBuildDayPortraitAndLifeFingerprint() {
        UUID owner = createUser("一天的主人");
        UUID stranger = createUser("另一位主人");
        UUID memorial = UUID.randomUUID();
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility,published_at) VALUES (?,?,?,?,?,'PUBLISHED','PUBLIC',CURRENT_TIMESTAMP)",
                memorial, owner, "day-" + UUID.randomUUID(), "饭团", "小猫");

        MemorialExperience.DayMoment morning = service.addDayMoment(memorial, owner, "07:10", "挠门叫我起床",
                "卧室门口", "挠三下就会停下来听里面的动静。", null);
        service.updateDayMoment(memorial, morning.id(), owner, "07:20", "挠门叫我起床",
                "卧室门口", "每天都很准时。", null);
        service.saveLifeDetail(memorial, owner, "QUIRK", "喝水前总要先拍两下水面。");

        MemorialExperience published = service.publicExperience(memorial);
        assertEquals("07:20", published.dayMoments().getFirst().momentTime());
        assertEquals("喝水前总要先拍两下水面。", published.lifeDetails().getFirst().answer());
        assertThrows(ApiException.class, () -> service.saveLifeDetail(memorial, owner, "OWNER_PHONE", "13800138000"));
        assertThrows(ApiException.class, () -> service.addDayMoment(memorial, owner, "25:00", "错误时间", null, null, null));
        assertThrows(ApiException.class, () -> service.saveLifeDetail(memorial, stranger, "QUIRK", "越权修改"));
    }

    @Test
    void weakCommunityOnlyReturnsExplicitlyPublicMemorials() {
        UUID owner = createUser("公开测试主人");
        UUID publicId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility,published_at) VALUES (?,?,?,?,?,'PUBLISHED','PUBLIC',CURRENT_TIMESTAMP)",
                publicId, owner, "public-" + UUID.randomUUID(), "星星", "小狗");
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility,published_at) VALUES (?,?,?,?,?,'PUBLISHED','LINK',CURRENT_TIMESTAMP)",
                linkId, owner, "link-" + UUID.randomUUID(), "月亮", "小猫");
        service.saveLifeDetail(publicId, owner, "QUIRK", "每天傍晚都在门口等我。");

        var discovered = memorialService.discoverPublic(48);
        assertTrue(discovered.stream().anyMatch(item -> item.id().equals(publicId)
                && "每天傍晚都在门口等我。".equals(item.signature())));
        assertFalse(discovered.stream().anyMatch(item -> item.id().equals(linkId)));
    }

    private UUID createUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,email,password_hash,display_name,status) VALUES (?,?,?,?,'ACTIVE')",
                id, id + "@example.com", "unused", name);
        jdbc.update("INSERT INTO user_roles (user_id,role) VALUES (?,'USER')", id);
        return id;
    }
}
