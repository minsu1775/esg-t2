package ai.claudecode.esgt2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Esgt2Application {

    public static void main(String[] args) {
        SpringApplication.run(Esgt2Application.class, args);
    }
}
