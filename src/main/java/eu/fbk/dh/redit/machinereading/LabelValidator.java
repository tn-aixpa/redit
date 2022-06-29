/**
 * Verifies if the label predicted by a classifier is compatible with the object itself
 * For example, in KBP you cannot have a "org:*" relation if the first argument is PER entity
 */
package eu.fbk.dh.redit.machinereading;

import eu.fbk.dh.redit.machinereading.structure.ExtractionObject;

public interface LabelValidator {
    public boolean validLabel(String label, ExtractionObject object);
}
