package org.yu.flow.module.release.dto;

/**
 * 加入版本单时的可选资产。
 */
public record ReleaseAssetOptionDTO(String assetType, String assetId, String name, String detail,
                                    boolean publishable, boolean published, boolean unpublishedChanges) {
}
