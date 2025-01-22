package eu.fbk.dh.redit.util;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

public class NERSpan {
	@Getter
	@Setter
	String label;
	@Getter
	@Setter
	Integer sentence;

	@Getter
	@Setter
	List<Integer> tokens = new ArrayList<>();

	public NERSpan(String label, Integer sentence, Integer token) {
		this.label = label;
		this.sentence = sentence;
		if (token >= 0) {
			this.tokens.add(token);
		}
	}

	public NERSpan(String label, Integer sentence, List<Integer> tokens) {
		this.label = label;
		this.sentence = sentence;
		this.tokens = tokens;
	}

	public void addToken(Integer token) {
		this.tokens.add(token);
	}

	@Override
	public String toString() {
		return "NERSpan{" + "label='" + label + '\'' + ", sentence=" + sentence + ", tokens=" + tokens + '}';
	}

	public String getLabel() {
		// TODO Auto-generated method stub
		return label;
	}

	public Integer getSentence() {
		// TODO Auto-generated method stub
		return sentence;
	}

	public void setSentence(Integer sentence1) {
		this.sentence = sentence1;
	}

	public List<Integer> getTokens() {
		// TODO Auto-generated method stub
		return tokens;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setTokens(List<Integer> tokens) {
		this.tokens = tokens;
	}

}
