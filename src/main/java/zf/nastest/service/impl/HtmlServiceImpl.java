package zf.nastest.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zf.nastest.entity.HtmlScore;
import zf.nastest.entity.dto.HtmlDTO;
import zf.nastest.mapper.HtmlMapper;
import zf.nastest.service.HtmlService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 该类是HtmlService接口的实现类，用于处理与html记录相关的业务逻辑。
 * 使用Spring的事务管理来确保数据操作的一致性。
 */
@Service
public class HtmlServiceImpl implements HtmlService {

    /**
     * 注入HtmlMapper，用于与数据库进行交互。
     */
    private final HtmlMapper htmlMapper;

    /**
     * 构造器注入Mapper依赖。
     * @param htmlMapper HTML Mapper
     */
    @Autowired
    public HtmlServiceImpl(HtmlMapper htmlMapper) {
        this.htmlMapper = htmlMapper;
    }

    /**
     * 查询所有的html记录，并将结果转换为HtmlDTO列表返回。
     * 使用只读事务，提高查询性能。
     * @return 包含所有html记录的HtmlDTO列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<HtmlDTO> selectHtml() {
        // 1. 查询数据库获取原始数据
        List<HtmlScore> scores = htmlMapper.selectHtml();
        // 2. 转换为DTO对象
        return scores.stream()
                .map(this::convertToHtmlDTO)
                .collect(Collectors.toList());
    }

    /**
     * 添加一个html记录。
     * 使用事务管理，确保数据的一致性。
     * @param htmlDTO HTML DTO 对象
     * @return 添加后的 HTML DTO 对象
     */
    @Override
    @Transactional
    public HtmlDTO addHtml(HtmlDTO htmlDTO) {
        HtmlScore htmlScore = new HtmlScore();
        htmlScore.setLink(htmlDTO.getLink());
        htmlScore.setTitle(htmlDTO.getTitle());
        htmlMapper.insertHtml(htmlScore);
        htmlDTO.setId(htmlScore.getId());
        return htmlDTO;
    }

    /**
     * 删除一个html记录。
     * 使用事务管理，确保数据的一致性。
     * @param id 记录 ID
     */
    @Override
    @Transactional
    public void deleteHtml(Long id) {
        htmlMapper.deleteHtml(id);
    }

    /**
     * 更新一个html记录。
     * 使用事务管理，确保数据的一致性。
     * @param htmlDTO HTML DTO 对象
     * @return 更新后的 HTML DTO 对象
     */
    @Override
    @Transactional
    public HtmlDTO updateHtml(HtmlDTO htmlDTO) {
        HtmlScore htmlScore = new HtmlScore();
        htmlScore.setId(htmlDTO.getId());
        htmlScore.setLink(htmlDTO.getLink());
        htmlScore.setTitle(htmlDTO.getTitle());
        htmlMapper.updateHtml(htmlScore);
        return htmlDTO;
    }

    /**
     * 将HtmlScore实体转换为HtmlDTO。
     * @param entity 数据库实体
     * @return DTO对象
     */
    private HtmlDTO convertToHtmlDTO(HtmlScore entity) {
        HtmlDTO dto = new HtmlDTO();
        dto.setId(entity.getId());
        dto.setLink(entity.getLink());
        dto.setTitle(entity.getTitle());
        return dto;
    }
}