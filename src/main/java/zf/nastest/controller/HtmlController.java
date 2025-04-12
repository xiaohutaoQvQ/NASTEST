package zf.nastest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zf.nastest.entity.dto.HtmlDTO;
import zf.nastest.service.HtmlService;

import java.util.List;

/**
 * 该类是一个RESTful风格的控制器，用于处理与html记录相关的HTTP请求。
 * 允许所有跨域请求，提供了查询、新增、删除和更新html记录的接口。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // 允许所有跨域请求
public class HtmlController {

    /**
     * 注入HtmlService，用于处理业务逻辑。
     */
    private final HtmlService htmlService;

    /**
     * 构造器注入服务依赖。
     * @param htmlService 查询服务
     */
    public HtmlController(HtmlService htmlService) {
        this.htmlService = htmlService;
    }

    /**
     * 处理GET请求，查询所有的html记录。
     * @return 包含所有html记录的HtmlDTO列表的响应实体
     */
    @GetMapping("/selectHtml")
    public ResponseEntity<List<HtmlDTO>> selectHtml() {
        List<HtmlDTO> data = htmlService.selectHtml();
        return ResponseEntity.ok(data);
    }

    /**
     * 处理POST请求，新增一个html记录。
     * @param htmlDTO 要新增的html记录的DTO对象
     * @return 新增后的HtmlDTO对象的响应实体
     */
    @PostMapping("/addHtml")
    public ResponseEntity<HtmlDTO> addHtml(@RequestBody HtmlDTO htmlDTO) {
        HtmlDTO addedHtml = htmlService.addHtml(htmlDTO);
        return ResponseEntity.ok(addedHtml);
    }

    /**
     * 处理DELETE请求，根据ID删除一个html记录。
     * @param id 要删除的html记录的ID
     * @return 包含删除成功信息的响应实体
     */
    @DeleteMapping("/deleteHtml/{id}")
    public ResponseEntity<String> deleteHtml(@PathVariable Long id) {
        htmlService.deleteHtml(id);
        return ResponseEntity.ok("HTML 记录删除成功");
    }

    /**
     * 处理PUT请求，更新一个html记录。
     * @param htmlDTO 包含要更新信息的HtmlDTO对象
     * @return 更新后的HtmlDTO对象的响应实体
     */
    @PutMapping("/updateHtml")
    public ResponseEntity<HtmlDTO> updateHtml(@RequestBody HtmlDTO htmlDTO) {
        HtmlDTO updatedHtml = htmlService.updateHtml(htmlDTO);
        return ResponseEntity.ok(updatedHtml);
    }

    /**
     * 全局异常处理（简化示例）
     * 实际开发建议使用@ControllerAdvice
     * 当控制器中抛出异常时，该方法会捕获异常并返回包含错误信息的响应实体。
     * @param e 捕获到的异常
     * @return 包含错误信息的响应实体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        return ResponseEntity.internalServerError()
                .body("服务异常: " + e.getMessage());
    }
}