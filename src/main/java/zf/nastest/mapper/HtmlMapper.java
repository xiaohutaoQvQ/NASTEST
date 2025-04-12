package zf.nastest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;
import zf.nastest.entity.HtmlScore;

import java.util.List;

/**
 * 该接口是MyBatis的Mapper接口，用于定义与数据库交互的方法。
 * 包含查询、新增、删除和更新html记录的方法。
 */
@Mapper
public interface HtmlMapper {

    /**
     * 查询所有的html记录，并将结果映射为HtmlScore列表返回。
     * @return 包含所有html记录的HtmlScore列表
     */
    List<HtmlScore> selectHtml();

    /**
     * 新增一个html记录到数据库中。
     * @param htmlScore 要新增的html记录的实体对象
     */
    void insertHtml(HtmlScore htmlScore);

    /**
     * 根据ID删除数据库中的一个html记录。
     * @param id 要删除的html记录的ID
     */
    void deleteHtml(@Param("id") Long id);

    /**
     * 更新数据库中的一个html记录。
     * @param htmlScore 包含要更新信息的html记录的实体对象
     */
    void updateHtml(HtmlScore htmlScore);
}