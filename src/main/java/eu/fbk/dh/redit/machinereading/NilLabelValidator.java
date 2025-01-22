package eu.fbk.dh.redit.machinereading;

import java.io.Serializable;

import eu.fbk.dh.redit.machinereading.structure.ExtractionObject;

public class NilLabelValidator implements Serializable, LabelValidator {

	private static final long serialVersionUID = 1L;

	public boolean validLabel(String label, ExtractionObject object) {
		return true;
	}

}
