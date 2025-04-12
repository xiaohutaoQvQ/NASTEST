package zf.nastest.config;

// 导入MyBatis提供的Mapper扫描注解，用于指定Mapper接口的扫描路径
import org.mybatis.spring.annotation.MapperScan;
// 导入Spring框架的配置类注解，用于标识该类为配置类
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis配置类
 * 此配置类的主要作用是配置MyBatis的Mapper接口扫描路径，
 * 使得Spring能够自动发现并注册这些Mapper接口，方便后续在业务代码中使用。
 * 同时，还可以在此类中添加分页插件等MyBatis的全局配置。
 */
// @Configuration注解表明这是一个Spring配置类，Spring会在启动时自动加载并处理这个类
@Configuration
// @MapperScan注解指定了MyBatis要扫描的Mapper接口所在的包路径，
// 这样MyBatis会自动为该包下的所有Mapper接口生成代理实现类
@MapperScan("zf.nastest.mapper")
public class MyBatisConfig {
    // 这是一个注释提示，表明可以在此处添加分页插件等MyBatis的全局配置。
    // 例如，如果需要使用分页插件，可以在这里进行相关的配置操作。
    // 目前该类仅完成了Mapper接口扫描路径的配置，后续可根据需求扩展其他配置。
}