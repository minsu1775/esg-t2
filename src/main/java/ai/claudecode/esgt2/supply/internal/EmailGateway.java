package ai.claudecode.esgt2.supply.internal;

/** 이메일 발송 게이트웨이. SmtpEmailGateway(운영) / StubEmailGateway(테스트)로 교체 가능. */
public interface EmailGateway {
    void sendInvitationEmail(String to, String tenantName, String activationLink);
    void sendReminderEmail(String to, String entityName, int reportingYear);
}
