package ai.claudecode.esgt2.ghg.api;

import java.io.InputStream;
import java.util.UUID;

public interface EvidenceFileService {
    EvidenceFileResponse upload(UUID tenantId, UUID uploadedBy, InputStream data,
                                String originalFilename, String mimeType);
}
