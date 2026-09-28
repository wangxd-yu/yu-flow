package org.yu.flow.module.release.dto;

import lombok.Data;

@Data
public class SaveReleaseDTO {

    /** 仅新建时生效 */
    private String code;

    private String name;

    private String remark;
}
