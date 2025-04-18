package eu.fbk.dh.utils.wemapp.machinereading.structure;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.ErasureUtils;
import eu.fbk.utils.gson.JSONLabel;

import java.util.List;

/**
 * Annotations specific to the machinereading data structures.
 *
 * @author Mihai
 */
public class MachineReadingAnnotations {

    private MachineReadingAnnotations() {
    } // only static members

    /**
     * The CoreMap key for getting the entity mentions corresponding to a sentence.
     * <p>
     * This key is typically set on sentence annotations.
     */
    @JSONLabel("entities")
    public static class EntityMentionsAnnotation implements CoreAnnotation<List<EntityMention>> {
        @Override
        public Class<List<EntityMention>> getType() {
            return ErasureUtils.<Class<List<EntityMention>>>uncheckedCast(List.class);
        }
    }

    /**
     * The CoreMap key for getting the relation mentions corresponding to a sentence.
     * <p>
     * This key is typically set on sentence annotations.
     */
    @JSONLabel("relations")
    public static class RelationMentionsAnnotation implements CoreAnnotation<List<RelationMention>> {
        @Override
        public Class<List<RelationMention>> getType() {
            return ErasureUtils.<Class<List<RelationMention>>>uncheckedCast(List.class);
        }
    }

    /**
     * The CoreMap key for getting relation mentions corresponding to a sentence.  Whereas
     * RelationMentionsAnnotation gives only relations pertaining to a test entity,
     * AllRelationMentionsAnnotation gives all pairwise relations.
     * <p>
     * This key is typically set on sentence annotations.
     */
    public static class AllRelationMentionsAnnotation implements CoreAnnotation<List<RelationMention>> {
        @Override
        public Class<List<RelationMention>> getType() {
            return ErasureUtils.<Class<List<RelationMention>>>uncheckedCast(List.class);
        }
    }

    /**
     * The CoreMap key for getting the event mentions corresponding to a sentence.
     * <p>
     * This key is typically set on sentence annotations.
     */
    public static class EventMentionsAnnotation implements CoreAnnotation<List<EventMention>> {
        @Override
        public Class<List<EventMention>> getType() {
            return ErasureUtils.<Class<List<EventMention>>>uncheckedCast(List.class);
        }
    }

    /**
     * The CoreMap key for getting the document id of a given sentence.
     * <p>
     * This key is typically set on sentence annotations.
     * <p>
     * NOTE: This is a trivial subclass of CoreAnnotations.DocIDAnnotation
     */
    @Deprecated
    public static class DocumentIdAnnotation extends CoreAnnotations.DocIDAnnotation {
        @Override
        public Class<String> getType() {
            return String.class;
        }
    }

    public static class DocumentDirectoryAnnotation implements CoreAnnotation<String> {
        @Override
        public Class<String> getType() {
            return String.class;
        }
    }

    /**
     * The CoreMap key for getting the syntactic dependencies of a sentence.
     * Note: this is no longer used, but it appears in sentences cached during KBP 2010.
     * <p>
     * This key is typically set on sentence annotations.
     */
    @Deprecated
    public static class DependencyAnnotation implements CoreAnnotation<SemanticGraph> {
        @Override
        public Class<SemanticGraph> getType() {
            return SemanticGraph.class;
        }
    }

    /**
     * Marks trigger words for relation extraction.
     *
     * @author Mihai
     */
    public static class TriggerAnnotation implements CoreAnnotation<String> {
        @Override
        public Class<String> getType() {
            return String.class;
        }
    }

    /**
     * Marks words as belonging to a list of either male or female names.
     */
    public static class GenderAnnotation implements CoreAnnotation<String> {
        @Override
        public Class<String> getType() {
            return String.class;
        }
    }

}
