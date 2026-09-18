package com.digitalnest.petmemorial.account;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccountDeletionPurgeJob {

    private final AccountDeletionRepository accountDeletionRepository;
    private final AccountDataService accountDataService;

    public AccountDeletionPurgeJob(
            AccountDeletionRepository accountDeletionRepository,
            AccountDataService accountDataService
    ) {
        this.accountDeletionRepository = accountDeletionRepository;
        this.accountDataService = accountDataService;
    }

    @Scheduled(fixedDelayString = "${app.privacy.deletion-purge-delay-ms:3600000}")
    public void purgeDueAccounts() {
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID userId : accountDeletionRepository.findDueUserIds(now)) {
            accountDataService.purgeDueAccount(userId, now);
        }
    }
}
