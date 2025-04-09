package zf.nastest.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class HtmlDTO {

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
}