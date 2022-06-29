package eu.fbk.dh.redit.machinereading;

import eu.fbk.dh.redit.machinereading.structure.ExtractionObject;

import java.io.Serializable;

public class NilLabelValidator implements Serializable, LabelValidator {

    private static final long serialVersionUID = 1L;

    public boolean validLabel(String label, ExtractionObject object) {
        return true;
    }


}
