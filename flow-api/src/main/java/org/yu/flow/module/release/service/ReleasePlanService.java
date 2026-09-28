package org.yu.flow.module.release.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.release.dto.AddOfflineItemsDTO;
import org.yu.flow.module.release.dto.AddReleaseItemsDTO;
import org.yu.flow.module.release.dto.ReleaseCompareDTO;
import org.yu.flow.module.release.dto.ReleaseScanResultDTO;
import org.yu.flow.module.release.dto.ReleaseAssetOptionDTO;
import org.yu.flow.module.release.dto.ReleaseCheckResultDTO;
import org.yu.flow.module.release.dto.ReleaseDTO;
import org.yu.flow.module.release.dto.SaveReleaseDTO;

import java.util.List;

/**
 * 版本单：维护一轮上线的资产清单，冻结后导出发布包。
 */
public interface ReleasePlanService {

    record PackageFile(String fileName, byte[] content) {
    }

    PageBean<ReleaseDTO> page(String keyword, String status, int page, int size);

    /** 当前可加入资产的版本单（编辑中），供列表页「加入版本单」选择 */
    List<ReleaseDTO> listDrafts();

    ReleaseDTO get(String id);

    ReleaseDTO create(SaveReleaseDTO dto);

    ReleaseDTO update(String id, SaveReleaseDTO dto);

    void delete(String id);

    ReleaseDTO addItems(String id, AddReleaseItemsDTO dto);

    /** 加入下线项（目标环境撤销发布 / 停用） */
    ReleaseDTO addOfflineItems(String id, AddOfflineItemsDTO dto);

    /**
     * 扫描自基线以来的变化：since 为空时以最近一个已导出版本的导出时间为起点。
     */
    ReleaseScanResultDTO scanChanges(String id, String since);

    /** 对比两个版本单 */
    ReleaseCompareDTO compare(String baseId, String targetId);

    ReleaseDTO removeItem(String id, String itemId);

    List<ReleaseAssetOptionDTO> searchAssets(String assetType, String keyword);

    /** 冻结前检查（只算不写） */
    ReleaseCheckResultDTO check(String id);

    /** 检查通过则冻结并记录内容指纹 */
    ReleaseCheckResultDTO freeze(String id);

    /** 回到编辑中；已导出的包作废，需重新冻结导出 */
    ReleaseDTO unfreeze(String id);

    PackageFile exportPackage(String id);
}
