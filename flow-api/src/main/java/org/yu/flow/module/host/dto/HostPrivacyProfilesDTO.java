package org.yu.flow.module.host.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class HostPrivacyProfilesDTO {

    private List<HostPrivacyProfileViewDTO> profiles = new ArrayList<>();
}
