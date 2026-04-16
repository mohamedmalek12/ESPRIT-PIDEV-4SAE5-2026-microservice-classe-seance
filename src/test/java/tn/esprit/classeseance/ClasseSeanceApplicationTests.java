package tn.esprit.classeseance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRabbitStubConfig.class)
class ClasseSeanceApplicationTests {

	@Test
	void contextLoads() {
	}

}
