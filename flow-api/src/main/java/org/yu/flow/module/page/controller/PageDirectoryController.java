package org.yu.flow.module.page.controller;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.dto.R;
import org.yu.flow.module.page.domain.PageDirectoryDO;
import org.yu.flow.module.page.dto.PageDirectoryDTO;
import org.yu.flow.module.page.service.PageDirectoryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 页面目录管理 Controller
 *
 * @author yu-flow
 */
@Slf4j
@RestController
@RequestMapping("flow-api/page-directories")
public class PageDirectoryController {

    @Resource
    private PageDirectoryService pageDirectoryService;

    /**
     * 获取目录树结构
     */
    @GetMapping("/tree")
    public R<List<PageDirectoryDTO>> getTree() {
        return R.ok(pageDirectoryService.getTree());
    }

    /**
     * 新增目录
     */
    @PostMapping
    public R<PageDirectoryDO> create(@RequestBody PageDirectoryDO directory) {
        return R.ok(pageDirectoryService.create(directory));
    }

    /**
     * 修改目录
     */
    @PutMapping("/{id}")
    public R<PageDirectoryDO> update(@PathVariable String id, @RequestBody PageDirectoryDO directory) {
        return R.ok(pageDirectoryService.update(id, directory));
    }

    /**
     * 删除目录（校验子节点和关联页面）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        pageDirectoryService.delete(id);
        return R.ok();
    }
}
