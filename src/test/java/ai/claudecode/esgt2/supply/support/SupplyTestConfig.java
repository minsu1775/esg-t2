package ai.claudecode.esgt2.supply.support;

import ai.claudecode.esgt2.supply.internal.EmailGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class SupplyTestConfig {

    /**
     * SmtpEmailGateway(@Component) 대신 StubEmailGateway를 주입.
     * StubEmailGateway는 EmailGateway를 구현하므로 DefaultSupplierService에 직접 주입 가능.
     * @Primary 로 SmtpEmailGateway보다 우선 선택됨.
     */
    @Bean
    @Primary
    public StubEmailGateway emailGateway() {
        return new StubEmailGateway();
    }
}
