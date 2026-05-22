package ai.claudecode.esgt2.supply.api;

import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;

import java.util.UUID;

public interface SupplierService {
    SupplierInvitationResponse inviteSupplier(UUID tenantId, UUID actorId,
                                               InviteSupplierRequest request);
    void activateAccount(ActivateSupplierRequest request);
    ActivityDataResponse submitData(UUID tenantId, UUID actorId,
                                    UUID entityId, SupplierDataRequest request);
    ActivityDataResponse submitForReview(UUID tenantId, UUID actorId, UUID activityDataId);
    ActivityDataResponse approveData(UUID tenantId, UUID actorId, UUID activityDataId);
    ActivityDataResponse rejectData(UUID tenantId, UUID actorId,
                                    UUID activityDataId, String reason);
}
