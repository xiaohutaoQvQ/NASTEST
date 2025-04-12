package zf.nastest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;
import zf.nastest.entity.HtmlScore;

import java.util.List;

@Mapper
public interface HtmlMapper {
    List<HtmlScore> selectHtml();

    // 新增方法
    void insertHtml(HtmlScore htmlScore);

    // 删除方法
    void deleteHtml(@Param("id") Long id);

    // 更新方法
    void updateHtml(HtmlScore htmlScore);
}