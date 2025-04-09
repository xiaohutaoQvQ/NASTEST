package zf.nastest.entity;


import jakarta.persistence.*;

/**
 * html实体类
 * 对应数据库表 html
 */
@Entity
@Table(name = "html")
public class HtmlScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    private String link;

    private String title;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return "HtmlScore{" +
                "id='" + id + '\'' +
                ", link='" + link + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
