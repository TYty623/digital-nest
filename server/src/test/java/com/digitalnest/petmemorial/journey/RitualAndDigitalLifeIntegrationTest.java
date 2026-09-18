package com.digitalnest.petmemorial.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalnest.petmemorial.digitallife.DigitalLifeRepository;
import com.digitalnest.petmemorial.digitallife.DigitalLifeService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RitualAndDigitalLifeIntegrationTest {
    @Autowired RitualService ritualService;
    @Autowired DigitalLifeService digitalLifeService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ownerCanBuildPrivateArchiveCalendarAndOneFreeCapsule() {
        UUID owner = createUser();
        UUID memorial = createMemorial(owner, "小满");

        ritualService.createArchive(memorial, owner, new RitualRepository.ArchiveCommand(
                "PLACE", "阳台最左边的垫子", "下午会在那里晒太阳。", LocalDate.now().minusYears(1),
                "家里的阳台", "我记得", "CONFIRMED", "PRIVATE", null));
        ritualService.createAnniversary(memorial, owner, new RitualRepository.AnniversaryCommand(
                "ADOPTION", "第一次回家", LocalDate.now().minusYears(2), "ANNUAL", false));
        ritualService.completeRitual(memorial, owner, new RitualRepository.RitualCommand(
                "SEASON", "LIGHT", "今天的阳光很像那时候。", false));
        ritualService.createCapsule(memorial, owner, new RitualRepository.CapsuleCommand(
                "明年春天再读", "希望你还记得那扇窗。", null, LocalDate.now().plusDays(1), "PRIVATE"));

        RitualSpace space = ritualService.space(memorial, owner);
        assertEquals(1, space.archiveEntries().size());
        assertEquals(1, space.anniversaries().size());
        assertEquals(1, space.rituals().size());
        assertEquals(1, space.capsules().size());
        assertThrows(ApiException.class, () -> ritualService.createCapsule(memorial, owner,
                new RitualRepository.CapsuleCommand("另一封", "第二封", null, LocalDate.now().plusDays(2), "PRIVATE")));
    }

    @Test
    void treasureOwnerCanConfirmTraceableFactsAndReceiveCitedMockAnswer() {
        UUID owner = createUser();
        UUID memorial = createMemorial(owner, "豆包");
        grantTreasure(owner);

        digitalLifeService.enable(memorial, owner, true, true, "DIGITAL_LIFE_V1");
        var fact = digitalLifeService.addFact(memorial, owner, new DigitalLifeRepository.FactCommand(
                "PREFERENCE", "豆包最喜欢在窗边晒午后的太阳。", "USER_NOTE", "家人的手写记录", "PENDING"));
        digitalLifeService.updateFactStatus(memorial, fact.id(), owner, "CONFIRMED");

        var answer = digitalLifeService.ask(memorial, owner, "TA 最喜欢在哪里？");
        assertTrue(answer.answer().contains("窗边"));
        assertEquals(1, answer.citations().size());
        assertEquals("家人的手写记录", answer.citations().getFirst().sourceLabel());

        digitalLifeService.delete(memorial, owner);
        assertTrue(digitalLifeService.workspace(memorial, owner).facts().isEmpty());
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,email,password_hash,display_name,status) VALUES (?,?,?,?,'ACTIVE')",
                id, id + "@example.com", "unused", "测试家人");
        jdbc.update("INSERT INTO user_roles (user_id,role) VALUES (?,'USER')", id);
        return id;
    }

    private UUID createMemorial(UUID owner, String petName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility) VALUES (?,?,?,?,?,'DRAFT','PRIVATE')",
                id, owner, "journey-" + UUID.randomUUID(), petName, "小狗");
        return id;
    }

    private void grantTreasure(UUID userId) {
        UUID order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO billing_orders (id,user_id,plan_code,plan_name,amount_cents,currency,photo_limit,short_video_limit,timeline_limit,theme_limit,hosted_years,status,idempotency_key,paid_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'PAID',?,CURRENT_TIMESTAMP)
                """, order, userId, "TREASURE", "时光珍藏", 29900, "CNY", 300, 15, 100, 3, 10, "journey-" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO account_entitlements (id,user_id,order_id,plan_code,plan_name,photo_limit,short_video_limit,timeline_limit,theme_limit,hosted_until,status)
                VALUES (?,?,?,?,?,?,?,?,?,?, 'ACTIVE')
                """, UUID.randomUUID(), userId, order, "TREASURE", "时光珍藏", 300, 15, 100, 3, OffsetDateTime.now().plusYears(1));
    }
}
