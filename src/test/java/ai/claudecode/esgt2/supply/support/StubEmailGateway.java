package ai.claudecode.esgt2.supply.support;

import ai.claudecode.esgt2.supply.internal.EmailGateway;

import java.util.ArrayList;
import java.util.List;

/** 테스트용 인메모리 이메일 게이트웨이. JavaMailSender 없이 이메일 동작을 검증한다. */
public class StubEmailGateway implements EmailGateway {

    public record SentInvitation(String to, String tenantName, String activationLink) {}
    public record SentReminder(String to, String entityName, int reportingYear) {}

    private final List<SentInvitation> sentInvitations = new ArrayList<>();
    private final List<SentReminder> sentReminders = new ArrayList<>();

    @Override
    public void sendInvitationEmail(String to, String tenantName, String activationLink) {
        sentInvitations.add(new SentInvitation(to, tenantName, activationLink));
    }

    @Override
    public void sendReminderEmail(String to, String entityName, int reportingYear) {
        sentReminders.add(new SentReminder(to, entityName, reportingYear));
    }

    public List<SentInvitation> getSentInvitations() { return sentInvitations; }
    public List<SentReminder> getSentReminders()     { return sentReminders; }
    public void clear() { sentInvitations.clear(); sentReminders.clear(); }
}
