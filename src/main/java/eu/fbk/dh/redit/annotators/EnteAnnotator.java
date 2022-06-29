package eu.fbk.dh.redit.annotators;

import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnteAnnotator implements Annotator {

    private LinearClassifier<String, String> classifier;
    Pattern idPatterns = Pattern.compile(".*-([0-9]+)-([0-9]+)$");

    public EnteAnnotator(String annotatorName, Properties props) {
        final String enteModel = props.getProperty(annotatorName + ".model");
        classifier = LinearClassifier.readClassifier(enteModel);
    }

    @Override
    public void annotate(Annotation annotation) {
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            String sentenceID = "sentence" + sentence.get(CoreAnnotations.SentenceIDAnnotation.class);
            try {
                List<Datum<String, String>> datumList = collectFeatures(sentenceID, sentence);
                for (Datum<String, String> datum : datumList) {
                    String label = classifier.classOf(datum);
                    if (label.equals("ENTE")) {
                        RVFDatum<String, String> rvfDatum = (RVFDatum<String, String>) datum;
                        Matcher matcher = idPatterns.matcher(rvfDatum.id());
                        if (matcher.find()) {
                            int startIndex = Integer.parseInt(matcher.group(1));
                            int endIndex = Integer.parseInt(matcher.group(2));
                            for (int i = startIndex; i <= endIndex; i++) {
                                CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(i);
                                token.set(CoreAnnotations.NamedEntityTagAnnotation.class, label);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
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
                CoreAnnotations.SentencesAnnotation.class,
                CoreAnnotations.PartOfSpeechAnnotation.class,
                CoreAnnotations.NamedEntityTagAnnotation.class
        )));
    }

    public static List<Datum<String, String>> collectFeatures(String idPrefix, CoreMap sentence) throws Exception {
        return collectFeatures(idPrefix, sentence, null);
    }

    public static List<Datum<String, String>> collectFeatures(String idPrefix, CoreMap sentence, @Nullable List<String> originalNers) throws Exception {
        Map<String, String> allowedNers = new HashMap();
        allowedNers.put("O", "O");
        allowedNers.put("ORG", "ORG");
        allowedNers.put("LOC", "ORG");
        allowedNers.put("ENTE", "ENTE");

        Set<Character> contentPosSet = new HashSet<>();
        contentPosSet.add('S');
        contentPosSet.add('A');
        contentPosSet.add('V');
        contentPosSet.add('B');

        List<CoreLabel> tokenList = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<String> ners = new ArrayList<>();
        if (originalNers == null) {
            for (int i = 0, getSize = tokenList.size(); i < getSize; i++) {
                CoreLabel token = tokenList.get(i);
                ners.add(token.ner());
            }
        } else {
            if (tokenList.size() > originalNers.size()) {
                int index = -1;
                for (int i = 0, getSize = tokenList.size(); i < getSize; i++) {
                    CoreLabel token = tokenList.get(i);
                    if (!token.isMWT() || (token.isMWT() && token.isMWTFirst())) {
                        index++;
                    }
                    ners.add(originalNers.get(index));
                }
            } else {
                ners = new ArrayList<>(originalNers);
            }
            if (tokenList.size() < originalNers.size()) {
                throw new Exception("Impossible!");
            }
        }

        // Clean NER
        for (int i = 0, nersSize = ners.size(); i < nersSize; i++) {
            String ner = ners.get(i);
            ners.set(i, allowedNers.getOrDefault(ner, "O"));
        }

        Map<Integer, Integer> entities = new HashMap<>();
        String oldNer = "O";
        int lastIndex = 0;
        for (int i = 0, nersSize = ners.size(); i < nersSize; i++) {
            String ner = ners.get(i);
            if (!ner.equals("O")) {
                if (oldNer.equals("O")) {
                    entities.put(i, i);
                    lastIndex = i;
                } else {
                    if (oldNer.equals(ner)) {
                        entities.put(lastIndex, i);
                    } else {
                        entities.put(i, i);
                        lastIndex = i;
                    }
                }
            }
            oldNer = ner;
        }

        List<Datum<String, String>> datumList = new ArrayList<>();
        for (Integer startIndex : entities.keySet()) {
            Counter<String> features = new ClassicCounter<>();

            // Tokens
            List<String> tokens = new ArrayList<>();
            for (int i = startIndex; i <= entities.get(startIndex); i++) {
                tokens.add(tokenList.get(i).get(CoreAnnotations.TextAnnotation.class));
            }

            String allTexts = String.join("_", tokens);

            features.setCount("firstWord-" + tokens.get(0).toLowerCase(), 1.0);
            features.setCount("lastWord-" + tokens.get(tokens.size() - 1).toLowerCase(), 1.0);
            features.setCount("textLower-" + allTexts.toLowerCase(), 1.0);
            features.setCount("text-" + allTexts, 1.0);

//            if (allUpper.matcher(allTexts).find()) {
//                features.setCount("allCaps", 1.0);
//            }

            for (String token : tokens) {
                features.setCount("tokenLower-" + token.toLowerCase(), 1.0);
                features.setCount("token-" + token, 1.0);
            }

            if (startIndex.equals(0)) {
                features.setCount("start", 1.0);
            } else {
                features.setCount("previous-" + tokenList.get(startIndex - 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase(), 1.0);
                for (int i = startIndex - 1; i >= 0; i--) {
                    char posFirst = tokenList.get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class).charAt(0);
                    if (contentPosSet.contains(posFirst)) {
                        features.setCount("cw-previous-" + tokenList.get(i).get(CoreAnnotations.TextAnnotation.class).toLowerCase(), 1.0);
                        break;
                    }
                }
            }

            if (entities.get(startIndex) == ners.size() - 1) {
                features.setCount("end", 1.0);
            } else {
                features.setCount("next-" + tokenList.get(entities.get(startIndex) + 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase(), 1.0);
                for (int i = entities.get(startIndex) + 1; i < tokenList.size(); i++) {
                    char posFirst = tokenList.get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class).charAt(0);
                    if (contentPosSet.contains(posFirst)) {
                        features.setCount("cw-next-" + tokenList.get(i).get(CoreAnnotations.TextAnnotation.class).toLowerCase(), 1.0);
                        break;
                    }
                }
            }

            String label = ners.get(startIndex);
            RVFDatum<String, String> datum = new RVFDatum<>(features, label);
            datum.setID(idPrefix + "-" + startIndex + "-" + entities.get(startIndex));
            datumList.add(datum);
        }

        return datumList;
    }

}
