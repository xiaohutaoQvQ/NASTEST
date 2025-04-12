// 指定该类所在的包路径
package zf.nastest.entity.dto;

// 导入 Lombok 库的 Data 注解，该注解会自动为类生成 getter、setter、toString、equals 和 hashCode 等方法
import lombok.Data;
// 导入 Lombok 库的 EqualsAndHashCode 注解，用于自动生成 equals 和 hashCode 方法
import lombok.EqualsAndHashCode;

/**
 * 该类是一个数据传输对象（DTO），用于在不同层之间传输 HTML 相关的数据，
 * 例如在控制器和服务层之间、服务层和持久层之间等。
 * 使用 Lombok 的 @Data 注解可以减少样板代码的编写。
 */
// 使用 @Data 注解，自动生成 getter、setter、toString、equals 和 hashCode 等方法
@Data
// 使用 @EqualsAndHashCode 注解，自动生成 equals 和 hashCode 方法
@EqualsAndHashCode
public class HtmlDTO {

    // 定义一个字符串类型的属性 id，用于存储 HTML 记录的唯一标识
    private String id;

    // 定义一个字符串类型的属性 link，用于存储 HTML 页面的链接
    private String link;

    // 定义一个字符串类型的属性 title，用于存储 HTML 页面的标题
    private String title;

    /**
     * 获取 HTML 记录的唯一标识
     * @return 存储在 id 属性中的字符串值
     */
    public String getId() {
        return id;
    }

    /**
     * 设置 HTML 记录的唯一标识
     * @param id 要设置的唯一标识字符串
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取 HTML 页面的链接
     * @return 存储在 link 属性中的字符串值
     */
    public String getLink() {
        return link;
    }

    /**
     * 设置 HTML 页面的链接
     * @param link 要设置的链接字符串
     */
    public void setLink(String link) {
        this.link = link;
    }

    /**
     * 获取 HTML 页面的标题
     * @return 存储在 title 属性中的字符串值
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置 HTML 页面的标题
     * @param title 要设置的标题字符串
     */
    public void setTitle(String title) {
        this.title = title;
    }
}