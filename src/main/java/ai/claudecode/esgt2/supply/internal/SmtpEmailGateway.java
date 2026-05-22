package ai.claudecode.esgt2.supply.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender mailSender;

    @Override
    public void sendInvitationEmail(String to, String tenantName, String activationLink) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("[" + tenantName + "] ESG 공급업체 포털 초대");
        msg.setText("공급업체 포털에 초대되었습니다.\n\n활성화 링크: " + activationLink +
                    "\n\n링크 유효 기간: 7일");
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("초대 이메일 발송 실패 (to={}): {}", to, e.getMessage());
            // 이메일 실패는 초대 자체를 실패시키지 않음 — 관리자가 수동 발송 가능
        }
    }

    @Override
    public void sendReminderEmail(String to, String entityName, int reportingYear) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("[ESG 포털] " + reportingYear + "년 활동 데이터 제출 리마인더");
        msg.setText("안녕하세요,\n\n" + entityName + " 법인의 " + reportingYear +
                    "년 활동 데이터가 아직 제출되지 않았습니다.\n\nESG 포털에 접속하여 데이터를 제출해 주세요.");
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("리마인더 이메일 발송 실패 (to={}, entity={}): {}", to, entityName, e.getMessage());
        }
    }
}
