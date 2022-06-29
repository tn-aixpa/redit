package eu.fbk.dh.redit.annotators;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class NumeroCivicoAnnotator implements Annotator {

    public Set<List<String>> types = new HashSet<>();
    public Set<String> numberTokens = new HashSet<>();

    public NumeroCivicoAnnotator() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("roadtypes.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.length() == 0) {
                    continue;
                }
                types.add(Arrays.asList(line.toLowerCase().split(" ")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        numberTokens.add("numero");
        numberTokens.add("n.");
        numberTokens.add(",");
    }

    @Override
    public void annotate(Annotation annotation) {
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
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
                if (!nerTag.equals("LOC")) {
                    continue;
                }
                Integer nerEnd = nerEnds.get(startKey);
                List<String> nerTokens = new ArrayList<>();
                for (int i = startKey; i <= nerEnd; i++) {
                    nerTokens.add(sentence.get(CoreAnnotations.TokensAnnotation.class).get(i).originalText().toLowerCase());
                }

                boolean found = false;
                for (List<String> type : types) {
                    int index = Collections.indexOfSubList(nerTokens, type);
                    if (index > -1) {
                        found = true;
                    }
                }

                if (!found) {
                    continue;
                }

                if (sentence.get(CoreAnnotations.TokensAnnotation.class).size() < nerEnd + 1 + 1) {
                    continue;
                }

                CoreLabel nextToken = sentence.get(CoreAnnotations.TokensAnnotation.class).get(nerEnd + 1);
                if (nextToken.ner().equals("NUMBER")) {
                    nextToken.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                }

                if (sentence.get(CoreAnnotations.TokensAnnotation.class).size() < nerEnd + 2 + 1) {
                    continue;
                }

                CoreLabel lastToken = sentence.get(CoreAnnotations.TokensAnnotation.class).get(nerEnd + 2);
                if (
                        lastToken.ner().equals("NUMBER") && (
                                numberTokens.contains(nextToken.originalText().toLowerCase()) ||
                                        nextToken.originalText().equals("/")
                        )
                ) {
                    nextToken.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                    lastToken.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                }

                if (sentence.get(CoreAnnotations.TokensAnnotation.class).size() < nerEnd + 3 + 1) {
                    continue;
                }
                CoreLabel lastToken3 = sentence.get(CoreAnnotations.TokensAnnotation.class).get(nerEnd + 3);
                if (
                        nextToken.ner().equals("NUMBER") &&
                                lastToken.originalText().equals("/") &&
                                lastToken3.ner().equals("NUMBER")

                ) {
                    nextToken.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                    lastToken.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                    lastToken3.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                }

                if (sentence.get(CoreAnnotations.TokensAnnotation.class).size() < nerEnd + 4 + 1) {
                    continue;
                }
                CoreLabel lastToken4 = sentence.get(CoreAnnotations.TokensAnnotation.class).get(nerEnd + 4);
                if (
                        numberTokens.contains(nextToken.originalText().toLowerCase()) &&
                                lastToken4.ner().equals("NUMBER") &&
                                lastToken.originalText().equals("/") &&
                                lastToken3.ner().equals("NUMBER")

                ) {
                    nextToken.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                    lastToken.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                    lastToken3.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                    lastToken4.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOC");
                }
            }

        }
    }

    @Override
    public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
        return Collections.emptySet();
    }

    @Override
    public Set<Class<? extends CoreAnnotation>> requires() {
        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
                CoreAnnotations.TokensAnnotation.class,
                CoreAnnotations.NamedEntityTagAnnotation.class
        )));
    }
}
