package zf.nastest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 这是一个Spring Boot应用的测试类，用于对应用的上下文加载进行测试。
 * 通过使用@SpringBootTest注解，Spring Boot会自动配置应用上下文，以便进行集成测试。
 */
@SpringBootTest
class NasTestApplicationTests {

    /**
     * 测试方法，用于验证Spring应用上下文是否能够成功加载。
     * 该方法目前为空，若上下文加载成功，则测试通过。
     */
    @Test
    void contextLoads() {
    }

}