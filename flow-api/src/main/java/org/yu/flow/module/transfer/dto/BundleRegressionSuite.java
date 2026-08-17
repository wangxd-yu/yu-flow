package org.yu.flow.module.transfer.dto;

import lombok.Data;
import org.yu.flow.module.release.domain.FlowRegressionCaseDO;
import org.yu.flow.module.release.domain.FlowRegressionSuiteDO;

import java.util.ArrayList;
import java.util.List;

/**
 * 回归套件及其用例。
 *
 * <p>生产环境的发布门禁默认要求回归通过，套件不随资产一起搬运的话，导入后的接口无法发布。</p>
 */
@Data
public class BundleRegressionSuite {

    private FlowRegressionSuiteDO suite;

    private List<FlowRegressionCaseDO> cases = new ArrayList<>();
}
