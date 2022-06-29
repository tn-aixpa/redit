package eu.fbk.dh.redit.util;

import lombok.Getter;

import java.util.List;
import java.util.TreeMap;

public class MainDocument {
    @Getter
    String fileName;
    @Getter
    TreeMap<Integer, TreeMap<Integer, String>> sentences;
    @Getter
    TreeMap<Integer, NERSpan> nerSpanMap;
    @Getter
    List<RelationPair> relations;

    public MainDocument(String fileName, TreeMap<Integer, TreeMap<Integer, String>> sentences, TreeMap<Integer, NERSpan> nerSpanMap, List<RelationPair> relations) {
        this.fileName = fileName;
        this.sentences = sentences;
        this.nerSpanMap = nerSpanMap;
        this.relations = relations;
    }
}
