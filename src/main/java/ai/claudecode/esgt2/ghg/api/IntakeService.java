package ai.claudecode.esgt2.ghg.api;

import org.springframework.core.io.Resource;

import java.util.List;
import java.util.UUID;

public interface IntakeService {
    CsvUploadResponse uploadCsv(UUID tenantId, UUID entityId, Resource csvFile);
    CsvUploadResponse receiveWebhook(UUID tenantId, List<WebhookActivityDataItem> items);
}
