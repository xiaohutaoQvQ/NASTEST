package zf.nastest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 这是Spring Boot应用的主类，用于启动应用。
 */
@SpringBootApplication
public class NasTestApplication {

    /**
     * 应用的入口方法，使用SpringApplication.run方法启动Spring Boot应用。
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NasTestApplication.class, args);
    }
}