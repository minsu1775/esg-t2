package ai.claudecode.esgt2.ghg.domain;

import java.io.InputStream;

public interface ObjectStorageGateway {
    /** 스트림을 storedFilename으로 저장하고 접근 URI를 반환. */
    String upload(InputStream data, String storedFilename, String mimeType);
}
