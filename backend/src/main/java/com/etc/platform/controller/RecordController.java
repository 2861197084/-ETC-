package com.etc.platform.controller;

import com.etc.platform.dto.ApiResponse;
import com.etc.platform.service.HBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/record")
public class RecordController {

    @Autowired
    private HBaseService hBaseService;

    @PostMapping("/page")
    public ApiResponse<Map<String, Object>> pageQuery(@RequestBody Map<String, Object> params) {
        Map<String, Object> data = hBaseService.pageQuery(params);
        return ApiResponse.ok(data);
    }

    /**
     * 兼容 GET 调试，便于直接在浏览器访问
     * 示例：/api/record/page?current=1&size=10&plate=粤A&gantryId=G001
     */
    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> pageQueryGet(@RequestParam Map<String, Object> params) {
        Map<String, Object> data = hBaseService.pageQuery(params);
        return ApiResponse.ok(data);
    }
}