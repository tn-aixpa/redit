package eu.fbk.dh.redit.machinereading.structure;

import edu.stanford.nlp.util.CoreMap;

import java.io.Serializable;

public class EntityMentionFactory implements Serializable {
    private static final long serialVersionUID = 47894791411048523L;

    /**
     * Always use this method to construct EntityMentions
     * Other factories that inherit from this (e.g., NFLEntityMentionFactory) may override this
     *
     * @param objectId
     * @param sentence
     * @param extentSpan
     * @param headSpan
     * @param type
     * @param subtype
     * @param mentionType
     */
    public EntityMention constructEntityMention(
            String objectId,
            CoreMap sentence,
            Span extentSpan,
            Span headSpan,
            String type,
            String subtype,
            String mentionType) {
        return new EntityMention(objectId, sentence, extentSpan, headSpan, type, subtype, mentionType);
    }
}
