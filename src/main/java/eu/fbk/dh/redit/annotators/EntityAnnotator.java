package eu.fbk.dh.redit.annotators;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import eu.fbk.dh.redit.machinereading.structure.AnnotationUtils;
import eu.fbk.dh.redit.machinereading.structure.EntityMention;
import eu.fbk.dh.redit.machinereading.structure.MachineReadingAnnotations;
import eu.fbk.dh.redit.machinereading.structure.Span;

import java.util.*;

public class EntityAnnotator implements Annotator {

    @Override
    public void annotate(Annotation annotation) {
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            Map<Integer, String> nerLabels = new HashMap<>();
            Map<Integer, Integer> nerEnds = new HashMap<>();
            String lastNer = "O";
            int lastIndex = -1;
            List<CoreLabel> get = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (int i = 0, getSize = get.size(); i < getSize; i++) {
                CoreLabel token = get.get(i);
                if (!token.ner().equals("O")) {
                    if (!token.ner().equals(lastNer)) {
                        nerLabels.put(i, token.ner());
                        nerEnds.put(i, i);
                        lastIndex = i;
                    } else {
                        nerEnds.put(lastIndex, i);
                    }
                }

                lastNer = token.ner();
            }

            for (Integer startKey : nerLabels.keySet()) {
                String nerTag = nerLabels.get(startKey);
                Integer nerEnd = nerEnds.get(startKey);
                String identifier = "entity-" + nerTag + "-" + startKey;
                Span extentSpan = new Span(startKey, nerEnd + 1);
                EntityMention entity = new EntityMention(identifier, sentence, extentSpan, extentSpan, nerTag, null, null);
                entity.setHeadTokenPosition(startKey);
                AnnotationUtils.addEntityMention(sentence, entity);
            }

        }

    }

    @Override
    public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
        return Collections.singleton(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    }

    @Override
    public Set<Class<? extends CoreAnnotation>> requires() {
        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
                CoreAnnotations.TokensAnnotation.class,
                CoreAnnotations.SentencesAnnotation.class,
                CoreAnnotations.NamedEntityTagAnnotation.class
        )));
    }
}
