package com.autoflow;

import com.autoflow.config.TestRedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@DisplayName("Spring Application Context Loading Test")
class AutoFlowApplicationTests {

    @Test
    @DisplayName("Application context should bootstrap successfully")
    void contextLoads() {
    }
}
