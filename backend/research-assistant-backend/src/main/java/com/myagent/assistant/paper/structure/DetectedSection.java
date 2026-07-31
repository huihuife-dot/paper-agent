package com.myagent.assistant.paper.structure;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 章节识别结果。
 */
@Data
@AllArgsConstructor
public class DetectedSection {
    private String title;
    private SectionType type;
    private String content;
    private Integer sectionIndex;

    public boolean isReference() {
        return SectionType.REFERENCES.equals(type);
    }

    public boolean isLowValueBackMatter() {
        return SectionType.BACK_MATTER.equals(type);
    }
}
