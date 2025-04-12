package zf.nastest.service;

import zf.nastest.entity.dto.HtmlDTO;

import java.util.List;

/**
 * 该接口定义了与html记录相关的业务逻辑方法。
 * 包括查询、新增、删除和更新html记录的方法。
 */
public interface HtmlService {

    /**
     * 查询所有的html记录，并将结果转换为HtmlDTO列表返回。
     * @return 包含所有html记录的HtmlDTO列表
     */
    List<HtmlDTO> selectHtml();

    /**
     * 新增一个html记录。
     * 接收一个HtmlDTO对象，将其保存到数据库中，并返回包含数据库生成ID的HtmlDTO对象。
     * @param htmlDTO 要新增的html记录的DTO对象
     * @return 新增后的HtmlDTO对象，包含数据库生成的ID
     */
    HtmlDTO addHtml(HtmlDTO htmlDTO);

    /**
     * 根据ID删除一个html记录。
     * @param id 要删除的html记录的ID
     */
    void deleteHtml(Long id);

    /**
     * 更新一个html记录。
     * 接收一个包含ID的HtmlDTO对象，将其信息更新到数据库中，并返回更新后的HtmlDTO对象。
     * @param htmlDTO 包含要更新信息的HtmlDTO对象
     * @return 更新后的HtmlDTO对象
     */
    HtmlDTO updateHtml(HtmlDTO htmlDTO);
}