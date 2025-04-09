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

@Service
public class HtmlServiceImpl implements HtmlService {

    private final HtmlMapper htmlMapper;

    /**
     * 构造器注入Mapper依赖
     * @param htmlMapper HTML Mapper
     */
    @Autowired
    public HtmlServiceImpl(HtmlMapper htmlMapper) {
        this.htmlMapper = htmlMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HtmlDTO> selectHtml() {
        // 1. 查询数据库获取原始数据
        List<HtmlScore> scores = htmlMapper.selectHtml();
//        System.out.println(scores.toString());
        // 2. 转换为DTO对象
        return scores.stream()
                .map(this::convertToHtmlDTO)
                .collect(Collectors.toList());
    }

    /**
     * 添加 HTML 记录
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
     * 删除 HTML 记录
     * @param id 记录 ID
     */
    @Override
    @Transactional
    public void deleteHtml(Long id) {
        htmlMapper.deleteHtml(id);
    }

    /**
     * 更新 HTML 记录
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
     * 将HtmlScore实体转换为HtmlDTO
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