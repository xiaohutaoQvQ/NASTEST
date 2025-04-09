package zf.nastest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zf.nastest.entity.dto.HtmlDTO;
import zf.nastest.service.HtmlService;

import java.util.List;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // 允许所有跨域请求
public class HtmlController {

    private final HtmlService htmlService;

    /**
     * 构造器注入服务依赖
     * @param htmlService 查询服务
     */
    public HtmlController(HtmlService htmlService) {
        this.htmlService = htmlService;
    }

    @GetMapping("/selectHtml")
    public ResponseEntity<List<HtmlDTO>> selectHtml() {
        List<HtmlDTO> data = htmlService.selectHtml();
        return ResponseEntity.ok(data);
    }

    @PostMapping("/addHtml")
    public ResponseEntity<HtmlDTO> addHtml(@RequestBody HtmlDTO htmlDTO) {
        HtmlDTO addedHtml = htmlService.addHtml(htmlDTO);
        return ResponseEntity.ok(addedHtml);
    }

    @DeleteMapping("/deleteHtml/{id}")
    public ResponseEntity<String> deleteHtml(@PathVariable Long id) {
        htmlService.deleteHtml(id);
        return ResponseEntity.ok("HTML 记录删除成功");
    }

    @PutMapping("/updateHtml")
    public ResponseEntity<HtmlDTO> updateHtml(@RequestBody HtmlDTO htmlDTO) {
        HtmlDTO updatedHtml = htmlService.updateHtml(htmlDTO);
        return ResponseEntity.ok(updatedHtml);
    }

    /**
     * 全局异常处理（简化示例）
     * 实际开发建议使用@ControllerAdvice
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        return ResponseEntity.internalServerError()
                .body("服务异常: " + e.getMessage());
    }
}