package com.digitalnest.petmemorial.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HabitatIntegrationTest {
    @Autowired HabitatService habitatService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ownerCanSaveAnOptionalPrivateCornerAndTraceItsMemory() {
        UUID owner = createUser();
        UUID memorial = createMemorial(owner);

        habitatService.saveSettings(memorial, owner, "COMPANION", "窗边旧时光", 70);
        habitatService.addItem(memorial, owner, "LIGHT", 51, 65);
        habitatService.addNote(memorial, owner, new HabitatService.HabitatNoteCommand(
                LocalDate.now().minusDays(2), "它总是在窗边等我回家。", "DAY_MOMENT", UUID.randomUUID(), "18:30 · 门口等我"));

        MemorialHabitatSpace space = habitatService.space(memorial, owner);
        assertEquals("COMPANION", space.scene());
        assertEquals(1, space.items().size());
        assertEquals("DAY_MOMENT", space.notes().getFirst().sourceType());
        assertThrows(ApiException.class, () -> habitatService.space(memorial, UUID.randomUUID()));
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,email,password_hash,display_name,status) VALUES (?,?,?,?,'ACTIVE')",
                id, id + "@example.com", "unused", "测试家人");
        jdbc.update("INSERT INTO user_roles (user_id,role) VALUES (?,'USER')", id);
        return id;
    }

    private UUID createMemorial(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO memorials (id,user_id,slug,pet_name,species,status,visibility) VALUES (?,?,?,?,?,'DRAFT','PRIVATE')",
                id, owner, "habitat-" + UUID.randomUUID(), "小满", "小狗");
        return id;
    }
}
