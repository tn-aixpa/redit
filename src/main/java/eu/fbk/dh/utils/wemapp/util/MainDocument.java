package eu.fbk.dh.utils.wemapp.util;

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

	public MainDocument(String fileName, TreeMap<Integer, TreeMap<Integer, String>> sentences,
			TreeMap<Integer, NERSpan> nerSpanMap, List<RelationPair> relations) {
		this.fileName = fileName;
		this.sentences = sentences;
		this.nerSpanMap = nerSpanMap;
		this.relations = relations;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public TreeMap<Integer, TreeMap<Integer, String>> getSentences() {
		return sentences;
	}

	public void setSentences(TreeMap<Integer, TreeMap<Integer, String>> sentences) {
		this.sentences = sentences;
	}

	public TreeMap<Integer, NERSpan> getNerSpanMap() {
		return nerSpanMap;
	}

	public void setNerSpanMap(TreeMap<Integer, NERSpan> nerSpanMap) {
		this.nerSpanMap = nerSpanMap;
	}

	public List<RelationPair> getRelations() {
		return relations;
	}

	public void setRelations(List<RelationPair> relations) {
		this.relations = relations;
	}

}
