package ai.claudecode.esgt2;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    ApplicationModules modules = ApplicationModules.of(Esgt2Application.class);

    @Test
    void 모듈_경계가_유효하다() {
        modules.verify();
    }

    @Test
    void 모듈_목록_출력() {
        modules.forEach(System.out::println);
    }
}
