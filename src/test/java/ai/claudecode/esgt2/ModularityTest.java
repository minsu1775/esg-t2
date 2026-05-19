package ai.claudecode.esgt2;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTest {

    ApplicationModules modules = ApplicationModules.of(Esgt2Application.class);

    @Test
    void 모듈_경계가_유효하다() {
        modules.verify();
    }

    @Test
    void 모듈_7개가_등록된다() {
        // ghg, entity, audit, vw, rpt, supply, shared
        assertThat(modules.stream().count()).isEqualTo(7);
    }
}
