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

	public int getOffset() {
		// TODO Auto-generated method stub
		return offset;
	}

	public String getType() {
		// TODO Auto-generated method stub
		return type;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public Integer getSentence() {
		return sentence;
	}

	public void setSentence(Integer sentence) {
		this.sentence = sentence;
	}

	public Integer getToken() {
		return token;
	}

	public void setToken(Integer token) {
		this.token = token;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setOffset(Integer offset) {
		this.offset = offset;
	}

}
