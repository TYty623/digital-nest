package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class MemorialCapacityConcurrencyTest {
    @Autowired MemorialService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentUploadsCannotExceedFreePhotoLimit() throws Exception {
        UUID user = UUID.randomUUID();
        UUID nest = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id) VALUES (?)", user);
        jdbc.update("INSERT INTO memorials (id, user_id, slug, pet_name, species) VALUES (?, ?, ?, '容量测试', '小狗')", nest, user, "capacity-" + nest);
        var mediaIds = new ArrayList<UUID>();
        for (int i = 0; i < 16; i++) {
            UUID media = UUID.randomUUID();
            jdbc.update("INSERT INTO media_assets (id, owner_id, storage_filename, content_type, byte_size) VALUES (?, ?, ?, 'image/png', 12)", media, user, media + ".png");
            mediaIds.add(media);
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            CountDownLatch start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (UUID media : mediaIds) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try { service.addGalleryItem(nest, user, media, null); return true; }
                    catch (ApiException exception) {
                        assertEquals("当前套餐的照片数量已满，请整理已有内容或升级套餐。", exception.getMessage());
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) if (future.get(20, TimeUnit.SECONDS)) successes++;
            assertEquals(12, successes);
            assertEquals(12, service.listGallery(nest, user).size());
        }
    }
}
