package eu.fbk.dh.redit.util;

import lombok.Getter;
import lombok.Setter;

public class Rule {
    String fileName;

    @Getter
    String type;

    @Getter
    @Setter
    Integer sentence;
    @Getter
    @Setter
    Integer token;
    @Getter
    @Setter
    Integer offset;

    public Rule(String fileName, String type) {
        this.fileName = fileName;
        this.type = type;
    }
}
