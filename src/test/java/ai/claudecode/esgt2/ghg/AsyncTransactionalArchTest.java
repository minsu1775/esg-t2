package ai.claudecode.esgt2.ghg;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTransactionalArchTest {

    @Test
    void Async_메서드에_Transactional_동시_부착_없음() throws ClassNotFoundException {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<String> violations = new ArrayList<>();

        for (var beanDef : provider.findCandidateComponents("ai.claudecode.esgt2")) {
            Class<?> clazz = Class.forName(beanDef.getBeanClassName());
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Async.class)
                        && method.isAnnotationPresent(Transactional.class)) {
                    violations.add(clazz.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(violations)
            .as("@Async + @Transactional 동시 부착 금지 (05-async-concurrency.md). 위반 메서드: %s", violations)
            .isEmpty();
    }
}
