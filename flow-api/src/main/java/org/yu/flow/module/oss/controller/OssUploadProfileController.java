package org.yu.flow.module.oss.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.dto.R;
import org.yu.flow.module.oss.domain.OssUploadProfileDO;
import org.yu.flow.module.oss.dto.OssUploadProfileDTO;
import org.yu.flow.module.oss.query.OssUploadProfileQueryDTO;
import org.yu.flow.module.oss.service.OssUploadProfileService;
import org.yu.flow.module.rbac.support.RequirePerm;

import java.util.List;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@YuFlowApi
@ConditionalOnOssEnabled
@RestController
@RequestMapping("/flow-api/oss/profiles")
@RequirePerm({"flow:oss:view", "flow:oss:write"})
public class OssUploadProfileController {

    @Resource
    private OssUploadProfileService ossUploadProfileService;

    @PostMapping
    public R<OssUploadProfileDTO> create(@RequestBody OssUploadProfileDO profileDO) {
        return R.ok(OssUploadProfileDTO.fromDO(ossUploadProfileService.save(profileDO)));
    }

    @PutMapping("/{id}")
    public R<OssUploadProfileDTO> update(@PathVariable String id, @RequestBody OssUploadProfileDO profileDO) {
        profileDO.setId(id);
        return R.ok(OssUploadProfileDTO.fromDO(ossUploadProfileService.update(profileDO)));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        ossUploadProfileService.delete(id);
        return R.ok();
    }

    @PutMapping("/batch/delete")
    public R<Void> batchDelete(@RequestBody List<String> ids) {
        ossUploadProfileService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<OssUploadProfileDTO> getById(@PathVariable String id) {
        return R.ok(OssUploadProfileDTO.fromDO(ossUploadProfileService.findById(id)));
    }

    @GetMapping("/page")
    public R<PageBean<OssUploadProfileDTO>> getPage(OssUploadProfileQueryDTO queryDTO) {
        return R.ok(ossUploadProfileService.findPage(queryDTO));
    }

    @GetMapping("/options")
    public R<List<OssUploadProfileDTO>> options() {
        return R.ok(ossUploadProfileService.listEnabled());
    }
}
