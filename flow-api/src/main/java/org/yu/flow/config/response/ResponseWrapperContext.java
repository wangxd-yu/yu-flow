package org.yu.flow.config.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseWrapperContext {
    private String successWrapper;
    private String pageWrapper;
    private String failWrapper;
}
