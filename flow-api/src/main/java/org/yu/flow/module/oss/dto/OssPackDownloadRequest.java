package org.yu.flow.module.oss.dto;

import lombok.Data;

import java.util.List;

@Data
public class OssPackDownloadRequest {

    private List<String> ids;
}
