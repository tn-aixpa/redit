package eu.fbk.dh.redit.util;

import lombok.Getter;

import java.util.List;
import java.util.Map;

public class Document {
    @Getter
    private String fileName;
    @Getter
    private List<List<String>> sentences;
    @Getter
    private List<List<String>> ners;
    @Getter
    private Map<Integer, Relation> relations;

    public Document(String fileName, List<List<String>> sentences, List<List<String>> ners, Map<Integer, Relation> relations) {
        this.fileName = fileName;
        this.sentences = sentences;
        this.ners = ners;
        this.relations = relations;
    }
}
