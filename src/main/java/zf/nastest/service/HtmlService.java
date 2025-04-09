package zf.nastest.service;

import zf.nastest.entity.dto.HtmlDTO;

import java.util.List;

/**
 * 接口
 * 定义业务逻辑方法
 */
public interface HtmlService {

    List<HtmlDTO> selectHtml();

    // 新增方法：接收HtmlDTO，返回新增后的对象（含数据库生成的ID等信息）
    HtmlDTO addHtml(HtmlDTO htmlDTO);

    // 删除方法：根据ID删除，无返回值（或可返回boolean表示是否成功）
    void deleteHtml(Long id);

    // 更新方法：接收包含ID的HtmlDTO，返回更新后的对象
    HtmlDTO updateHtml(HtmlDTO htmlDTO);
}