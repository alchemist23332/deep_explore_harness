package com.alchemist.deepexplore.agent.application;

import com.alchemist.deepexplore.agent.domain.ToolDescriptor;
import java.util.List;

public final class ToolDescriptorTestFixtures {

    private ToolDescriptorTestFixtures() {
    }

    public static ToolDescriptorRegistry registry() {
        return new ToolDescriptorRegistry(List.of(() -> List.of(
                new ToolDescriptor(
                        "web_search",
                        "网页搜索",
                        ToolDescriptor.ArgumentExposure.QUERY,
                        ToolDescriptor.ResultExposure.NONE
                ),
                new ToolDescriptor(
                        "read_file",
                        "读取文件",
                        ToolDescriptor.ArgumentExposure.DESCRIPTION,
                        ToolDescriptor.ResultExposure.SUMMARY
                )
        )));
    }
}
