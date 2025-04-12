package zf.nastest.entity;

import jakarta.persistence.*;

/**
 * html实体类，对应数据库表html。
 * 该类用于映射数据库中的html表，包含了表中各个字段的属性和对应的getter、setter方法。
 */
@Entity
@Table(name = "html")
public class HtmlScore {

    /**
     * 该字段是html记录的唯一标识，使用数据库自增策略生成ID。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    /**
     * 该字段存储html页面的链接地址。
     */
    private String link;

    /**
     * 该字段存储html页面的标题。
     */
    private String title;

    /**
     * 获取html记录的唯一标识。
     * @return html记录的ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置html记录的唯一标识。
     * @param id 要设置的ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取html页面的链接地址。
     * @return html页面的链接
     */
    public String getLink() {
        return link;
    }

    /**
     * 设置html页面的链接地址。
     * @param link 要设置的链接
     */
    public void setLink(String link) {
        this.link = link;
    }

    /**
     * 获取html页面的标题。
     * @return html页面的标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置html页面的标题。
     * @param title 要设置的标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 重写toString方法，用于方便打印对象信息。
     * @return 包含对象属性信息的字符串
     */
    @Override
    public String toString() {
        return "HtmlScore{" +
                "id='" + id + '\'' +
                ", link='" + link + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}