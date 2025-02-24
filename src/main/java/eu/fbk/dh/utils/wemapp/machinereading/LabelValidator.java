/**
 * Verifies if the label predicted by a classifier is compatible with the object itself
 * For example, in KBP you cannot have a "org:*" relation if the first argument is PER entity
 */
package eu.fbk.dh.utils.wemapp.machinereading;

import eu.fbk.dh.utils.wemapp.machinereading.structure.ExtractionObject;

public interface LabelValidator {
    public boolean validLabel(String label, ExtractionObject object);
}
