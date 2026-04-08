package org.yu.flow.module.page.controller;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.auto.dto.BatchMoveDTO;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.page.query.PageInfoQueryDTO;
import org.yu.flow.module.page.domain.PageInfoDO;
import org.yu.flow.module.page.dto.PageInfoDTO;
import org.yu.flow.module.page.service.PageInfoService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 页面信息管理 Controller
 *
 * @author yu-flow
 */
@Slf4j
@RestController
@RequestMapping("flow-api/pages")
public class PageInfoController {

    @Resource
    private PageInfoService pageInfoService;

    /**
     * 校验页面访问路径是否已被占用
     *
     * @param path 页面访问路径
     * @return true-已占用, false-可用
     */
    @GetMapping("/check-path")
    public R<Boolean> checkPath(@RequestParam String path) {
        return R.ok(pageInfoService.existsByRoutePath(path));
    }

    /**
     * 分页查询页面列表
     *
     * @param queryDTO 查询条件（directoryId, name, routePath, page, size）
     */
    @GetMapping("/page")
    public R<PageBean<PageInfoDTO>> getPage(PageInfoQueryDTO queryDTO) {
        return R.ok(pageInfoService.findPage(queryDTO));
    }

    /**
     * 获取页面详情（含 schema，用于设计器回显）
     */
    @GetMapping("/{id}")
    public R<PageInfoDTO> getById(@PathVariable String id) {
        return R.ok(pageInfoService.findById(id));
    }

    /**
     * 新建页面
     */
    @PostMapping
    public R<PageInfoDO> create(@RequestBody PageInfoDO pageInfo) {
        return R.ok(pageInfoService.create(pageInfo));
    }

    /**
     * 批量移动
     */
    @PutMapping("/batch/moveToDir")
    public R<Void> batchMove(@RequestBody BatchMoveDTO batchMoveDTO) {
        pageInfoService.batchMove(batchMoveDTO.getIds(), batchMoveDTO.getTargetDirectoryId());
        return R.ok();
    }

    /**
     * 更新基础信息（不含 schema）
     */
    @PutMapping("/{id}")
    public R<PageInfoDO> update(@PathVariable String id, @RequestBody PageInfoDO pageInfo) {
        return R.ok(pageInfoService.update(id, pageInfo));
    }

    /**
     * 保存设计器生成的 JSON Schema
     *
     * 请求体示例：{ "json": "{\"type\":\"page\",\"body\":[...]}" }
     */
    @PutMapping("/{id}/json")
    public R<PageInfoDO> updateJson(@PathVariable String id, @RequestBody Map<String, String> body) {
        String json = body.get("json");
        return R.ok(pageInfoService.updateJson(id, json));
    }

    /**
     * 切换发布状态
     *
     * 请求体示例：{ "status": 1 }
     */
    @PutMapping("/{id}/status")
    public R<PageInfoDO> updateStatus(@PathVariable String id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        return R.ok(pageInfoService.updateStatus(id, status));
    }

    /**
     * 克隆页面
     */
    @PostMapping("/{id}/clone")
    public R<PageInfoDO> clonePage(@PathVariable String id) {
        return R.ok(pageInfoService.clonePage(id));
    }

    /**
     * 删除页面
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        pageInfoService.delete(id);
        return R.ok();
    }


}
