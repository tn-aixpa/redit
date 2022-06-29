package eu.fbk.dh.redit;

import com.google.common.collect.HashMultimap;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.stats.PrecisionRecallStats;
import edu.stanford.nlp.util.CoreMap;
import eu.fbk.dh.redit.annotators.EnteAnnotator;
import eu.fbk.dh.redit.machinereading.GenericDataSetReader;
import eu.fbk.dh.redit.machinereading.structure.*;
import eu.fbk.dh.redit.util.Relation;
import eu.fbk.dh.redit.util.*;
import eu.fbk.dh.tint.runner.TintPipeline;
import eu.fbk.utils.core.CommandLine;
import eu.fbk.utils.core.FrequencyHashSet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KindRelationsReader_backup extends GenericDataSetReader {

    private static Map<String, String> relReplace = new HashMap<>();
    private static Map<String, String> nerReplace = new HashMap<>();

    private static File defaultTintSerFileName = new File("tint.ser");

    private static final Pattern allLower = Pattern.compile("[^A-Z]*?[a-z]+[^A-Z]*?");
    private static final Pattern allUpper = Pattern.compile("[^a-z]*?[A-Z]+[^a-z]*?");
    private static final Pattern startUpper = Pattern.compile("[A-Z].*");

    private File saveRawNerFile = null;
    private File saveRawEntityList = null;
    private File saveWebAnnoRelationFiles = null;
    private File tintSerFile = null;
    private File enteModelFile = null;

    private static final Pattern nersPattern = Pattern.compile("([A-Za-z0-9]+|\\\\_)(\\[([0-9]+)\\])?");
    private static final Pattern relInfoPattern = Pattern.compile("^([0-9-]+).*");

    private static final Pattern stPattern = Pattern.compile("^([0-9]+)-([0-9]+)$");
    private static final Pattern relPattern = Pattern.compile("^(([0-9]+)-([0-9]+))\\[([0-9]+)_([0-9]+)\\]$");

    private Boolean fixCase = true;
    private Boolean useSplit = true;
    private Boolean parseTint = true;
    private Boolean performCrossValidation = false;

    private static Map<String, String> convertNer = new HashMap<>();

    private static Set<String> skipFiles = new HashSet<>();

    static {
        convertNer.put("TEL", "NUMBER");
        convertNer.put("FAX", "NUMBER");
        convertNer.put("VAT", "NUMBER");
        convertNer.put("DOCID", "NUMBER");
    }

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public void setSaveRawNerFile(File saveRawNerFile) {
        this.saveRawNerFile = saveRawNerFile;
    }

    public void setSaveRawEntityList(File saveRawEntityList) {
        this.saveRawEntityList = saveRawEntityList;
    }

    public void setParseTint(Boolean parseTint) {
        this.parseTint = parseTint;
    }

    public void setSaveWebAnnoRelationFiles(File saveWebAnnoRelationFiles) {
        this.saveWebAnnoRelationFiles = saveWebAnnoRelationFiles;
    }

    public void setTintSerFile(File tintSerFile) {
        this.tintSerFile = tintSerFile;
    }

    public void setEnteModelFile(File enteModelFile) {
        this.enteModelFile = enteModelFile;
    }

    public void setPerformCrossValidation(Boolean performCrossValidation) {
        this.performCrossValidation = performCrossValidation;
    }

    public Document loadTsvDocument_backup(Path x) throws Exception {
        String fullFileName = x.toString();
        BufferedReader reader = new BufferedReader(new FileReader(fullFileName));

        List<List<String>> sentences = new ArrayList<>();
        List<List<String>> ners = new ArrayList<>();
        Map<Integer, Relation> relations = new HashMap<>();

        Set<String[]> relationLines = new HashSet<>();
        Map<String, Integer> tokenMapping = new HashMap<>();

        List<String> thisSentence = new ArrayList<>();
        List<String> thisNerList = new ArrayList<>();

        boolean mergeSentences = false;

        String line;
        boolean lastIsUnderscore = false;
        while (true) {
            line = reader.readLine();
            if (line != null) {
                line = line.trim();
                if (line.startsWith("#")) {
                    continue;
                }
            }

            if (line == null || line.length() == 0) {
                if ((mergeSentences || lastIsUnderscore) && line != null) {
                    mergeSentences = false;
                    lastIsUnderscore = false;
                } else if (thisSentence.size() > 0) {
                    sentences.add(thisSentence);
                    ners.add(thisNerList);

                    for (String[] parts : relationLines) {
                        String relationNames = parts[4];
                        String relationsLinks = parts[5];
                        String[] relationList = relationNames.split("\\|");
                        String[] relationInfo = relationsLinks.split("\\|");

                        relationLoop:
                        for (int i = 0, relationListLength = relationList.length; i < relationListLength; i++) {
                            String relationName = relationList[i];

                            Matcher matcher = relInfoPattern.matcher(relationInfo[i]);
                            if (matcher.find()) {
                                String relationToken = matcher.group(1);
//                                                        System.out.println(relationToken);
                                Integer relationSource = tokenMapping.get(parts[0]);
                                Integer relationLink = tokenMapping.get(relationToken);

                                String firstNer = thisNerList.get(relationSource);

                                boolean switchEntities = false;
                                switch (relationName) {
                                    case "op":
                                    case "docType":
                                        if (!firstNer.equals("PER")) {
                                            switchEntities = true;
                                        }
                                        break;

                                    case "name":
                                    case "relative":
                                        if (!firstNer.equals("PER")) {
                                            System.err.println("Invalid relation: " + relationName + " - in file: " + fullFileName);
                                            break relationLoop;
                                        }
                                        break;

                                    case "cig":
                                    case "cup":
                                        if (!firstNer.equals("ORG")) {
                                            switchEntities = true;
                                        }
                                        break;

                                    case "tel":
                                    case "email":
                                    case "vat":
                                    case "cf":
                                    case "companyRole":
                                    case "personalRole":
                                    case "birthDate":
                                    case "deathDate":
                                        if (!firstNer.equals("PER") && !firstNer.equals("ORG")) {
                                            switchEntities = true;
                                        }
                                        break;

                                    case "addressR":
                                    case "addressD":
                                        relationName = "address";
                                    case "address":
                                    case "birthLoc":
                                    case "deathLoc":
                                        if (firstNer.equals("LOC")) {
                                            switchEntities = true;
                                        }
                                        break;

                                    case "docID":
                                    case "docIssueDate":
                                    case "docIssueLoc":
                                    case "docExpDate":
                                        break;

                                    default:
                                        System.err.println("Invalid relation: " + relationList[i] + " - in file: " + fullFileName);
                                        break relationLoop;
                                }

                                try {
                                    Relation relation = new Relation(relationName, sentences.size() - 1);
                                    if (switchEntities) {
                                        relation.set("main", relationLink);
                                        relation.set("slave", relationSource);
                                    } else {
                                        relation.set("main", relationSource);
                                        relation.set("slave", relationLink);
                                    }

                                    int size = relations.size();
                                    relations.put(size, relation);
                                } catch (NullPointerException e) {
//                                                            System.out.println(fullFileName);
//                                                            System.out.println(thisSentence);
//                                                            System.out.println(thisNerList);
//                                                            System.out.println(tokenMapping);
//                                                            System.out.println(relationName + "-" + firstNer + " --- " + relationSource + "-" + relationLink);
                                    System.err.println("Relation between different sentences in file: " + fullFileName);
//                                                            throw new Exception(e);
                                }
                            } else {
                                throw new Exception("Tokens not found for relation " + relationName);
                            }
                        }
                    }

                    relationLines = new HashSet<>();
                    tokenMapping = new HashMap<>();

                    thisSentence = new ArrayList<>();
                    thisNerList = new ArrayList<>();
                }
            }

            if (line == null) {
                break;
            }

            String[] parts = line.split("\t");
            if (parts.length < 4) {
                continue;
            }

            tokenMapping.put(parts[0], thisSentence.size());
            String token = parts[2];
            thisSentence.add(token);

            String ner = parts[3];
            if (ner.contains("\\_")) {
                lastIsUnderscore = true;
            } else {
                lastIsUnderscore = false;
            }
            if (ner.equals("_") || ner.equals("\\_")) {
                ner = "O";
            } else {
                String[] nerParts = ner.split("\\|");
                for (String nerPart : nerParts) {
                    if (nerPart.startsWith("\\_")) {
                        continue;
                    }
                    Matcher matcher = nersPattern.matcher(nerPart);
                    if (matcher.find()) {
                        ner = matcher.group(1);
                    } else {
                        System.err.println("Invalid NER: " + ner + " - in file: " + fullFileName);
                    }
                }
            }

            thisNerList.add(ner);

            if (parts.length < 5) {
                continue;
            }

            String relationNames = parts[4];
            if (!relationNames.equals("_")) {
                if (relationNames.startsWith("\\_")) {
                    mergeSentences = true;
                } else {
                    relationLines.add(parts);
                }
            }
        }

        Document document = new Document(x.getFileName().toString(), sentences, ners, relations);
        reader.close();
        return document;
    }

    public MainDocument loadTsvDocument(Path x) throws Exception {
        String fullFileName = x.toString();
        FileInputStream fileInputStream = new FileInputStream(x.toFile());
        BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));

//        logger.info(x.getFileName().toString());

        TreeMap<Integer, TreeMap<Integer, String>> sentences = new TreeMap<>();
        TreeMap<Integer, NERSpan> nerSpanMap = new TreeMap<>();
        List<RelationPair> relations = new ArrayList<>();

        Map<String, NERSpan> nerSpanMap2 = new TreeMap<>();
        Map<String, Integer> nerSpanIndexMap = new HashMap<>();

        String rLine;

        // First pass: NERs
        while ((rLine = reader.readLine()) != null) {
            String[] parts = rLine.split("\t");
            rLine = rLine.trim();
            if (rLine.startsWith("#")) {
                continue;
            }

            Matcher matcher = stPattern.matcher(parts[0]);
            if (!matcher.find()) {
                continue;
            }
            int sentenceID = Integer.parseInt(matcher.group(1));
            int tokenID = Integer.parseInt(matcher.group(2));
            sentences.putIfAbsent(sentenceID, new TreeMap<>());
            sentences.get(sentenceID).putIfAbsent(tokenID, parts[2]);

            String ner = parts[3];
            String[] nerParts = ner.split("\\|");
            for (String nerPart : nerParts) {
                Matcher nerMatcher = nersPattern.matcher(nerPart);
                if (!nerMatcher.find()) {
                    continue;
                }

                String label = nerMatcher.group(1);
                String id = nerMatcher.group(3);
                if (id == null) {
                    // NER without ID (single token)
                    NERSpan nerSpan = new NERSpan(label, sentenceID, tokenID);
                    nerSpanMap2.put(parts[0], nerSpan);
                } else {
                    Integer idInt = Integer.parseInt(id);
                    nerSpanMap.putIfAbsent(idInt, new NERSpan(label, sentenceID, -1));
                    nerSpanMap.get(idInt).addToken(tokenID);
                }
            }
        }

        Integer lastIndex = Collections.max(nerSpanMap.keySet());
        for (String key : nerSpanMap2.keySet()) {
            lastIndex++;
            nerSpanMap.put(lastIndex, nerSpanMap2.get(key));
            nerSpanIndexMap.put(key, lastIndex);
        }

        // Second pass: relations
        fileInputStream.getChannel().position(0);
        while ((rLine = reader.readLine()) != null) {
            String[] parts = rLine.split("\t");
            rLine = rLine.trim();
            if (rLine.startsWith("#")) {
                continue;
            }
            if (parts.length < 6) {
                continue;
            }

            String[] relationList = parts[4].split("\\|");
            String[] relationInfoList = parts[5].split("\\|");

            if (relationList.length != relationInfoList.length) {
                throw new Exception("Lists of relations differ in length");
            }

            for (int i = 0, relationListLength = relationList.length; i < relationListLength; i++) {
                String relationName = relationList[i];
                String relationInfo = relationInfoList[i];

                Matcher relMatcher = relPattern.matcher(relationInfo);
                if (!relMatcher.find()) {
                    continue;
                }
                String stIndex = relMatcher.group(1);
//                Integer relSentenceID = Integer.parseInt(relMatcher.group(2));
//                Integer relTokenID = Integer.parseInt(relMatcher.group(3));
                Integer otherEntityID = Integer.parseInt(relMatcher.group(4));
                Integer thisEntityID = Integer.parseInt(relMatcher.group(5));

                if (thisEntityID.equals(0)) {
                    thisEntityID = nerSpanIndexMap.get(parts[0]);
                }
                if (otherEntityID.equals(0)) {
                    otherEntityID = nerSpanIndexMap.get(stIndex);
                }

                RelationPair relationPair = new RelationPair(relationName, thisEntityID, otherEntityID);
                relations.add(relationPair);
            }
        }

        TreeSet<Integer> relationsToRemove = new TreeSet<>();
        TreeSet<Integer> entitiesToRemove = new TreeSet<>();
        for (int i = 0, relationsSize = relations.size(); i < relationsSize; i++) {
            RelationPair relation = relations.get(i);
            if (relation.getLabel().equals("\\_")) {
                NERSpan entity1 = nerSpanMap.get(relation.getEntity1());
                NERSpan entity2 = nerSpanMap.get(relation.getEntity2());
                try {
                    mergeSentences(sentences, nerSpanMap, relations, entity1.getSentence(), entity2.getSentence());
                    if (entity1.getLabel().equals("\\_")) {
                        entitiesToRemove.add(relation.getEntity1());
                    }
                    if (entity2.getLabel().equals("\\_")) {
                        entitiesToRemove.add(relation.getEntity2());
                    }
                } catch (Exception e) {
                    // probably entity1 or entity2 do not exists anymore
                    // no problem here
                }
                relationsToRemove.add(i);
            }
        }

        for (Integer index : relationsToRemove.descendingSet()) {
            relations.remove(index.intValue());
        }
        for (Integer index : entitiesToRemove.descendingSet()) {
            nerSpanMap.remove(index);
        }

        // NERs with \_ in a relation are already removed here
        entitiesToRemove = new TreeSet<>();
        for (Integer key : nerSpanMap.keySet()) {
            NERSpan nerSpan = nerSpanMap.get(key);
            if (nerSpan.getLabel().equals("\\_")) {
                entitiesToRemove.add(key);
            }
        }

        for (Integer index : entitiesToRemove.descendingSet()) {
            NERSpan nerSpan = nerSpanMap.get(index);
            try {
                int sent = nerSpan.getSentence();
                mergeSentences(sentences, nerSpanMap, relations, sent, sent + 1);
                nerSpanMap.remove(index);
            } catch (Exception e) {
                // this happens because in the mergeSentences call
                // the nerSpan has already been removed
            }
        }

        relationLoop:
        for (RelationPair relation : relations) {
            boolean switchEntities = false;
            NERSpan entity1 = nerSpanMap.get(relation.getEntity1());
//            NERSpan entity2 = nerSpanMap.get(relation.getEntity2());
            String relationName = relation.getLabel();

            String firstNer = null;
            try {
                firstNer = entity1.getLabel();
            } catch (Exception e) {
                System.out.println(x.getFileName());
                System.out.println(entitiesToRemove);
                System.out.println(relation);
                System.out.println(nerSpanMap.keySet());
                System.exit(1);
            }

            switch (relation.getLabel()) {
                case "op":
                case "docType":
                    if (!firstNer.equals("PER")) {
                        switchEntities = true;
                    }
                    break;

                case "name":
                case "relative":
                    if (!firstNer.equals("PER")) {
                        System.err.println("Invalid relation: " + relationName + " - in file: " + fullFileName);
                        break relationLoop;
                    }
                    break;

                case "cig":
                case "cup":
                    if (!firstNer.equals("ORG")) {
                        switchEntities = true;
                    }
                    break;

                case "tel":
                case "email":
                case "vat":
                case "cf":
                case "companyRole":
                case "personalRole":
                case "birthDate":
                case "deathDate":
                    if (!firstNer.equals("PER") && !firstNer.equals("ORG")) {
                        switchEntities = true;
                    }
                    break;

                case "addressR":
                case "addressD":
                    relationName = "address";
                case "address":
                case "birthLoc":
                case "deathLoc":
                    if (firstNer.equals("LOC")) {
                        switchEntities = true;
                    }
                    break;

                case "docID":
                case "docIssueDate":
                case "docIssueLoc":
                case "docExpDate":
                    break;

                default:
                    System.err.println("Invalid relation: " + relationName + " - in file: " + fullFileName);
                    break relationLoop;
            }

            if (switchEntities) {
                int tmp = relation.getEntity1();
                relation.setEntity1(relation.getEntity2());
                relation.setEntity2(tmp);
            }

            relation.setLabel(relationName);
        }

//        for (Integer key : sentences.keySet()) {
//            System.out.println(key + " --- " + sentences.get(key));
//        }
//        for (Integer key : nerSpanMap.keySet()) {
//            System.out.println(key + " --- " + nerSpanMap.get(key));
//        }
//        for (int i = 0, relationsSize = relations.size(); i < relationsSize; i++) {
//            RelationPair relation = relations.get(i);
//            System.out.println(i + " --- " + relation);
//        }

        return new MainDocument(x.toString(), sentences, nerSpanMap, relations);

//        System.exit(1);
//
//        List<List<String>> mSentences = new ArrayList<>();
//        List<List<String>> ners = new ArrayList<>();
//        Map<Integer, eu.fbk.dh.utils.wemapp.util.Relation> mRelations = new HashMap<>();
//
//        Set<String[]> relationLines = new HashSet<>();
//        Map<String, Integer> tokenMapping = new HashMap<>();
//
//        List<String> thisSentence = new ArrayList<>();
//        List<String> thisNerList = new ArrayList<>();
//
//        boolean mergeSentences = false;
//
//        String line;
//        boolean lastIsUnderscore = false;
//        while (true) {
//            line = reader.readLine();
//            if (line != null) {
//                line = line.trim();
//                if (line.startsWith("#")) {
//                    continue;
//                }
//            }
//
//            if (line == null || line.length() == 0) {
//                if ((mergeSentences || lastIsUnderscore) && line != null) {
//                    mergeSentences = false;
//                    lastIsUnderscore = false;
//                } else if (thisSentence.size() > 0) {
//                    mSentences.add(thisSentence);
//                    ners.add(thisNerList);
//
//                    for (String[] parts : relationLines) {
//                        String relationNames = parts[4];
//                        String relationsLinks = parts[5];
//                        String[] relationList = relationNames.split("\\|");
//                        String[] relationInfo = relationsLinks.split("\\|");
//
//                        relationLoop:
//                        for (int i = 0, relationListLength = relationList.length; i < relationListLength; i++) {
//                            String relationName = relationList[i];
//
//                            Matcher matcher = relInfoPattern.matcher(relationInfo[i]);
//                            if (matcher.find()) {
//                                String relationToken = matcher.group(1);
////                                                        System.out.println(relationToken);
//                                Integer relationSource = tokenMapping.get(parts[0]);
//                                Integer relationLink = tokenMapping.get(relationToken);
//
//                                String firstNer = thisNerList.get(relationSource);
//
//                                boolean switchEntities = false;
//                                switch (relationName) {
//                                    case "op":
//                                    case "docType":
//                                        if (!firstNer.equals("PER")) {
//                                            switchEntities = true;
//                                        }
//                                        break;
//
//                                    case "name":
//                                    case "relative":
//                                        if (!firstNer.equals("PER")) {
//                                            System.err.println("Invalid relation: " + relationName + " - in file: " + fullFileName);
//                                            break relationLoop;
//                                        }
//                                        break;
//
//                                    case "cig":
//                                    case "cup":
//                                        if (!firstNer.equals("ORG")) {
//                                            switchEntities = true;
//                                        }
//                                        break;
//
//                                    case "tel":
//                                    case "email":
//                                    case "vat":
//                                    case "cf":
//                                    case "companyRole":
//                                    case "personalRole":
//                                    case "birthDate":
//                                    case "deathDate":
//                                        if (!firstNer.equals("PER") && !firstNer.equals("ORG")) {
//                                            switchEntities = true;
//                                        }
//                                        break;
//
//                                    case "addressR":
//                                    case "addressD":
//                                        relationName = "address";
//                                    case "address":
//                                    case "birthLoc":
//                                    case "deathLoc":
//                                        if (firstNer.equals("LOC")) {
//                                            switchEntities = true;
//                                        }
//                                        break;
//
//                                    case "docID":
//                                    case "docIssueDate":
//                                    case "docIssueLoc":
//                                    case "docExpDate":
//                                        break;
//
//                                    default:
//                                        System.err.println("Invalid relation: " + relationList[i] + " - in file: " + fullFileName);
//                                        break relationLoop;
//                                }
//
//                                try {
//                                    eu.fbk.dh.utils.wemapp.util.Relation relation = new eu.fbk.dh.utils.wemapp.util.Relation(relationName, mSentences.size() - 1);
//                                    if (switchEntities) {
//                                        relation.set("main", relationLink);
//                                        relation.set("slave", relationSource);
//                                    } else {
//                                        relation.set("main", relationSource);
//                                        relation.set("slave", relationLink);
//                                    }
//
//                                    int size = mRelations.size();
//                                    mRelations.put(size, relation);
//                                } catch (NullPointerException e) {
//                                    System.err.println("Relation between different sentences in file: " + fullFileName);
//                                }
//                            } else {
//                                throw new Exception("Tokens not found for relation " + relationName);
//                            }
//                        }
//                    }
//
//                    relationLines = new HashSet<>();
//                    tokenMapping = new HashMap<>();
//
//                    thisSentence = new ArrayList<>();
//                    thisNerList = new ArrayList<>();
//                }
//            }
//
//            if (line == null) {
//                break;
//            }
//
//            String[] parts = line.split("\t");
//            if (parts.length < 4) {
//                continue;
//            }
//
//            tokenMapping.put(parts[0], thisSentence.size());
//            String token = parts[2];
//            thisSentence.add(token);
//
//            String ner = parts[3];
//            if (ner.contains("\\_")) {
//                lastIsUnderscore = true;
//            } else {
//                lastIsUnderscore = false;
//            }
//            if (ner.equals("_") || ner.equals("\\_")) {
//                ner = "O";
//            } else {
//                String[] nerParts = ner.split("\\|");
//                for (String nerPart : nerParts) {
//                    if (nerPart.startsWith("\\_")) {
//                        continue;
//                    }
//                    Matcher matcher = nersPattern.matcher(nerPart);
//                    if (matcher.find()) {
//                        ner = matcher.group(1);
//                    } else {
//                        System.err.println("Invalid NER: " + ner + " - in file: " + fullFileName);
//                    }
//                }
//            }
//
//            thisNerList.add(ner);
//
//            if (parts.length < 5) {
//                continue;
//            }
//
//            String relationNames = parts[4];
//            if (!relationNames.equals("_")) {
//                if (relationNames.startsWith("\\_")) {
//                    mergeSentences = true;
//                } else {
//                    relationLines.add(parts);
//                }
//            }
//        }
//
//        Document document = new Document(x.getFileName().toString(), mSentences, ners, mRelations);
//        reader.close();
//        return document;
    }

    private void mergeSentences(TreeMap<Integer, TreeMap<Integer, String>> sentences, Map<Integer, NERSpan> nerSpanMap, List<RelationPair> relations, Integer sentence1, Integer sentence2) {

        if (sentence1.equals(sentence2)) {
            return;
        }

        // sentence2 is the greatest
        if (sentence2 < sentence1) {
            Integer tmp = sentence1;
            sentence1 = sentence2;
            sentence2 = tmp;
        }

        boolean inside = false;
        for (int i = sentence1 + 1; i < sentence2; i++) {
            if (sentences.containsKey(i)) {
                inside = true;
            }
        }

        if (inside) {
            logger.warning("Sentences are not close!");
            return;
        }

        Integer offset = Collections.max(sentences.get(sentence1).keySet());
        try {
            for (Integer key : sentences.get(sentence2).keySet()) {
                String token = sentences.get(sentence2).get(key);
                key += offset;
                sentences.get(sentence1).put(key, token);
            }
            sentences.remove(sentence2);
        } catch (Exception e) {
            // this happens when one of the two sentences has already been merged
            return;
        }

        // Update NER spans
        // (and check if a border one exists)
        Integer exists = null;
        for (Integer key : nerSpanMap.keySet()) {
            NERSpan nerSpan = nerSpanMap.get(key);
            if (nerSpan.getSentence().equals(sentence2)) {
                nerSpan.setSentence(sentence1);
                List<Integer> newTokens = new ArrayList<>();
                List<Integer> tokens = nerSpan.getTokens();
                for (Integer token : tokens) {
                    newTokens.add(token + offset);
                    if (token.equals(1)) {
                        exists = key;
                    }
                }
                nerSpan.setTokens(newTokens);
            }
        }

        // Merge two border spans
        if (exists != null) {
            boolean remove = false;
            Integer replace = null;
            for (Integer key : nerSpanMap.keySet()) {
                NERSpan nerSpan = nerSpanMap.get(key);
                if (nerSpan.getSentence().equals(sentence1)) {
                    List<Integer> tokens = nerSpan.getTokens();
                    List<Integer> tokensToAdd = new ArrayList<>();
                    for (Integer token : tokens) {
                        if (token.equals(offset)) {
                            NERSpan nerSpanToDelete = nerSpanMap.get(exists);
                            if (nerSpanToDelete.getLabel().equals(nerSpan.getLabel())) {
                                tokensToAdd.addAll(nerSpanToDelete.getTokens());
                                remove = true;
                                replace = key;
                            }
                        }
                    }
                    nerSpan.getTokens().addAll(tokensToAdd);
                }
            }
            if (remove) {
                nerSpanMap.remove(exists);

                // check whether the entity is involved in a relation
                for (RelationPair relation : relations) {
                    if (relation.getEntity1().equals(exists)) {
                        relation.setEntity1(replace);
                    }
                    if (relation.getEntity2().equals(exists)) {
                        relation.setEntity2(replace);
                    }
                }

            }
        }

    }

    public KindRelationsReader_backup() {
        super(null, false, true, true);
        InputStream resourceAsStream;

        resourceAsStream = getClass().getClassLoader().getResourceAsStream("relationsReplace.tsv");
        if (resourceAsStream == null) {
            throw new IllegalArgumentException("File not found!");
        } else {
            try {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT
                        .withCommentMarker('#')
                        .withDelimiter('\t')
                        .parse(new InputStreamReader(resourceAsStream));
                for (CSVRecord record : records) {
                    relReplace.put(record.get(0), record.get(1));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        resourceAsStream = getClass().getClassLoader().getResourceAsStream("nerToReplace.txt");
        if (resourceAsStream == null) {
            throw new IllegalArgumentException("File not found!");
        } else {
            try {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT
                        .withCommentMarker('#')
                        .withDelimiter('\t')
                        .parse(new InputStreamReader(resourceAsStream));
                for (CSVRecord record : records) {
                    if (record.size() == 1) {
                        nerReplace.put(record.get(0), record.get(0));
                    } else {
                        nerReplace.put(record.get(0), record.get(1));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

//        System.out.println(relReplace);
//        System.out.println(nerReplace);
//        System.exit(1);

        logger = Logger.getLogger(KindRelationsReader_backup.class.getName());
        logger.setLevel(Level.INFO);
    }

    public void setFixCase(Boolean fixCase) {
        this.fixCase = fixCase;
    }

    public void setUseSplit(Boolean useSplit) {
        this.useSplit = useSplit;
    }

    private void populateDocuments(String path, List<MainDocument> documents) throws IOException {

        Files.walk(Paths.get(path))
                .filter(Objects::nonNull)
                .filter(Files::isRegularFile)
                .filter(c -> c.getFileName().toString().substring(c.getFileName().toString().length() - 4).contains(".tsv"))
                .forEach(x -> {
                            try {
                                MainDocument mainDocument = loadTsvDocument(x);
//                                Document document = convertDocument(mainDocument);
                                documents.add(mainDocument);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                );

//        Files.walk(Paths.get(path))
//                .filter(Objects::nonNull)
//                .filter(Files::isRegularFile)
//                .filter(c -> c.getFileName().toString().substring(c.getFileName().toString().length() - 5).contains(".xlsx"))
//                .forEach(x -> {
//                    try {
//                        String fullFileName = x.toString();
//
//                        if (x.getFileName().toString().startsWith("~")) {
//                            return;
//                        }
//
//                        InputStream inp = new FileInputStream(fullFileName);
//                        Workbook wb = WorkbookFactory.create(inp);
//                        Sheet sheet = wb.getSheetAt(0);
//
//                        List<List<String>> sentences = new ArrayList<>();
//                        List<List<String>> ners = new ArrayList<>();
//                        Map<Integer, eu.fbk.dh.utils.wemapp.util.Relation> relations = new HashMap<>();
//
//                        List<String> thisSentence = new ArrayList<>();
//                        List<String> thisNerList = new ArrayList<>();
//
//                        int sentenceID = 0;
//                        int tokenID = 0;
//                        int lastSentencePopulated = 0;
//
//                        for (Row row : sheet) {
//                            Iterator<Cell> cellIterator = row.cellIterator();
//                            while (cellIterator.hasNext()) {
//                                Cell cell = cellIterator.next();
//                                String cellContent = dataFormatter.formatCellValue(cell);
//                                cellContent = cellContent.trim();
//
//                                if (cellContent.equals("")) {
//                                    continue;
//                                }
//
//                                int rowIndex = cell.getRowIndex();
//                                int colIndex = cell.getColumnIndex();
//
//                                if (Math.abs(rowIndex - lastSentencePopulated) > 1) {
//                                    sentences.add(thisSentence);
//                                    ners.add(thisNerList);
//                                    thisSentence = new ArrayList<>();
//                                    thisNerList = new ArrayList<>();
//                                    sentenceID++;
//                                    tokenID = 0;
//                                }
//                                lastSentencePopulated = rowIndex;
//                                if (colIndex == 0) {
//                                    thisSentence.add(cellContent);
//                                }
//                                if (colIndex == 1) {
//                                    thisNerList.add(cellContent);
//                                }
//                                if (colIndex == 2) {
//                                    int lastIndex = thisNerList.size() - 1;
//                                    thisNerList.set(lastIndex, cellContent);
//                                }
//
//                                if (colIndex > 2) {
//                                    try {
//                                        if (relReplace.containsKey(cellContent)) {
//                                            cellContent = relReplace.get(cellContent);
//                                        }
//                                        String[] parts = cellContent.split("_");
//                                        if (relations.containsKey(colIndex)) {
//                                            eu.fbk.dh.utils.wemapp.util.Relation relation = relations.get(colIndex);
//                                            if (relation.hasBoth()) {
//                                                throw new Exception("Error, three relations in the same column! " +
//                                                        relation + " in " + fullFileName);
//                                            }
//                                            if (relation.getSentenceID() != sentenceID) {
//                                                throw new Exception("Error, sentence IDs mismatch! " +
//                                                        relation.getSentenceID() + "/" + sentenceID + " in " + fullFileName);
//                                            }
//                                            if (!relation.getLabel().equals(parts[0])) {
//                                                throw new Exception("Error, relation names mismatch! " +
//                                                        relation + "/" + parts[0] + " in " + fullFileName);
//                                            }
//                                            relation.set(parts[1], tokenID);
//                                        } else {
//                                            eu.fbk.dh.utils.wemapp.util.Relation relation = new eu.fbk.dh.utils.wemapp.util.Relation(parts[0], sentenceID);
//                                            relation.set(parts[1], tokenID);
//                                            relations.put(colIndex, relation);
//                                        }
//                                    } catch (Exception e) {
//                                        logger.warning(e.getMessage());
//                                    }
//                                }
//                            }
//                            tokenID++;
//                        }
//
//                        if (thisSentence.size() > 0) {
//                            try {
//                                assert thisSentence.size() == thisNerList.size();
//                            } catch (AssertionError e) {
//                                logger.info(fullFileName);
//                                logger.info(thisSentence.toString());
//                                throw new AssertionError(e);
//                            }
//                            sentences.add(thisSentence);
//                            ners.add(thisNerList);
//                        }
//
//                        for (Integer key : relations.keySet()) {
//                            eu.fbk.dh.utils.wemapp.util.Relation relation = relations.get(key);
//                            if (!relation.hasBoth()) {
//                                logger.warning(fullFileName + ": Error, incomplete relation!");
//                            }
//                        }
//
//                        assert sentences.size() == ners.size();
//
//                        Document document = new Document(x.getFileName().toString(), sentences, ners, relations);
//                        documents.add(document);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                });

        // Check that everything is ok
//        for (Document document : documents) {
//            for (Integer key : document.getRelations().keySet()) {
//                eu.fbk.dh.utils.wemapp.util.Relation relation = document.getRelations().get(key);
//                assert relation.getLabel() != null;
//                assert document.getSentences().get(relation.getSentenceID()).get(relation.getMainTokenID()) != null;
//                assert document.getSentences().get(relation.getSentenceID()).get(relation.getSlaveTokenID()) != null;
////                System.out.println(relation.getLabel());
////                System.out.println(document.getSentences().get(relation.getSentenceID()).get(relation.getMainTokenID()));
////                System.out.println(document.getNers().get(relation.getSentenceID()).get(relation.getMainTokenID()));
////                System.out.println(document.getSentences().get(relation.getSentenceID()).get(relation.getSlaveTokenID()));
////                System.out.println(document.getNers().get(relation.getSentenceID()).get(relation.getSlaveTokenID()));
////                System.out.println();
//            }
//        }

        logger.info(documents.size() + " documents read");
    }

    @Override
    public Annotation read(String path) throws Exception {

        logger.info("Reading documents");
        List<MainDocument> documents = new ArrayList<>();
        populateDocuments(path, documents);

        FrequencyHashSet<String> nerCount = new FrequencyHashSet<>();
        FrequencyHashSet<String> relCount = new FrequencyHashSet<>();

        for (MainDocument document : documents) {
            for (RelationPair relation : document.getRelations()) {
                relCount.add(relation.getLabel());
            }
            for (NERSpan nerSpan : document.getNerSpanMap().values()) {
                nerCount.add(nerSpan.getLabel());
            }
        }

//        for (Document document : documents) {
//            for (Integer index : document.getRelations().keySet()) {
//                relCount.add(document.getRelations().get(index).getLabel());
//            }
//            for (List<String> ners : document.getNers()) {
//                String previousNer = "O";
//                for (String ner : ners) {
//                    if (!ner.equals(previousNer)) {
//                        if (!ner.equals("O") && nerReplace.containsKey(ner)) {
//                            nerCount.add(ner);
//                        }
//                    }
//                    previousNer = ner;
//                }
//            }
//        }

        System.out.println("### STATISTICS ###");
        Set<String> indexes;

        System.out.println("### Relations ###");
        indexes = new TreeSet<>(relCount.keySet());
        for (String index : indexes) {
            System.out.println(index + " " + relCount.get(index));
        }

        System.out.println("### Entities ###");
        indexes = new TreeSet<>(nerCount.keySet());
        for (String index : indexes) {
            System.out.println(index + " " + nerCount.get(index));
        }

        /*
        if (saveWebAnnoRelationFiles != null) {

            logger.info("Saving WebAnno files");
            if (!saveWebAnnoRelationFiles.exists()) {
                saveWebAnnoRelationFiles.mkdirs();
            }

            for (MainDocument document : documents) {
//                Map<Integer, HashMultimap<Integer, eu.fbk.dh.utils.wemapp.util.Relation>> relations = new HashMap<>();
                String outputFile = saveWebAnnoRelationFiles.getAbsolutePath() + File.separator + document.getFileName();
                if (outputFile.endsWith(".xlsx")) {
                    outputFile += ".tsv";
                } else {
                    continue;
                }

                for (eu.fbk.dh.utils.wemapp.util.Relation relation : document.getRelations().values()) {
                    relations.putIfAbsent(relation.getSentenceID(), HashMultimap.create());
                    relations.get(relation.getSentenceID()).put(relation.getMainTokenID(), relation);
                }

                StringBuffer buffer = new StringBuffer();

                buffer.append("#FORMAT=WebAnno TSV 3.3\n" +
                        "#T_SP=custom.Span|label\n" +
                        "#T_RL=custom.Relation|label|BT_custom.Span\n");
                buffer.append("\n");
                buffer.append("\n");

                int sentenceID = 1;
                int nerID = 0;
                int offset = 0;
                boolean reset = true;

                List<List<String>> sentences = document.getSentences();
                List<Map<Integer, Integer>> nerMaps = new ArrayList<>();

                for (int j = 0, sentencesSize = sentences.size(); j < sentencesSize; j++) {
                    List<String> tokens = sentences.get(j);
                    List<String> ners = document.getNers().get(j);

                    Map<Integer, Integer> nerMap = new HashMap<>();

                    String previousNer = "O";
                    for (int i = 0, tokensSize = tokens.size(); i < tokensSize; i++) {
                        String ner = ners.get(i);
                        if (ner.equals("O") || !ner.equals(previousNer)) {
                            reset = true;
                        }
                        if (!ner.equals("O")) {
                            if (reset) {
                                nerID++;
                                reset = false;
                            }
                            nerMap.put(i, nerID);
                        }
                        previousNer = ner;
                    }

                    nerMaps.add(nerMap);
                }

                nerID = 0;
                reset = true;

                // Second pass
                for (int j = 0, sentencesSize = sentences.size(); j < sentencesSize; j++) {
                    List<String> tokens = sentences.get(j);
                    List<String> ners = document.getNers().get(j);

                    HashMultimap<Integer, eu.fbk.dh.utils.wemapp.util.Relation> relationsInSentence = relations.getOrDefault(j, HashMultimap.create());
                    Map<Integer, Integer> nerMap = nerMaps.get(j);

                    buffer.append("#Text=").append(String.join(" ", tokens)).append("\n");
                    String previousNer = "O";
                    int tokenID = 1;
                    for (int i = 0, tokensSize = tokens.size(); i < tokensSize; i++) {
                        Set<eu.fbk.dh.utils.wemapp.util.Relation> relationsForToken = relationsInSentence.get(i);

                        StringBuilder buffer1 = new StringBuilder();
                        StringBuilder buffer2 = new StringBuilder();

                        if (relationsForToken.size() == 0) {
                            buffer1.append("_");
                            buffer2.append("_");
                        } else {
                            for (eu.fbk.dh.utils.wemapp.util.Relation relation : relationsForToken) {
                                buffer1.append(relation.getLabel()).append("|");
                                buffer2.append(String.format("%d-%d[%d_0]", sentenceID, relation.getSlaveTokenID() + 1, nerMap.get(relation.getSlaveTokenID()))).append("|");
                            }
                            buffer1.setLength(buffer1.length() - 1);
                            buffer2.setLength(buffer2.length() - 1);
                        }

                        String token = tokens.get(i);
                        String ner = ners.get(i);
                        ner = convertNer.getOrDefault(ner, ner);
                        if (ner.equals("O") || !ner.equals(previousNer)) {
                            reset = true;
                        }
                        String printNer = "_";
                        if (!ner.equals("O")) {
                            if (reset) {
                                nerID++;
                                reset = false;
                            }
                            printNer = String.format("%s[%d]", ner, nerID);
                        }
                        buffer.append(String.format("%d-%d\t%d-%d\t%s\t%s\t%s\t%s\t\n", sentenceID, tokenID++, offset, offset + token.length(), token, printNer, buffer1.toString().trim(), buffer2.toString().trim()));
                        offset += token.length() + 1;
                        previousNer = ner;
                    }

                    buffer.append("\n");
                    sentenceID++;
                }

                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
                writer.write(buffer.toString().trim());
                writer.close();
            }
        }
        */

        if (!parseTint) {
            return null;
        }

        Annotation retAnnotation = new Annotation("");

        List<List<String>> tintTokens = new ArrayList<>();
        List<List<String>> tintNers = new ArrayList<>();

        BufferedWriter entityWriter = null;
        if (saveRawEntityList != null) {
            logger.info("Writing file " + saveRawEntityList);
            entityWriter = new BufferedWriter(new FileWriter(saveRawEntityList));
        }

        logger.info("Parsing documents");
        Map<String, Map<Integer, Annotation>> tintAnnotations = new HashMap<>();

        TintPipeline pipeline = null;
        boolean loadTint = false;

        if (defaultTintSerFileName != null && defaultTintSerFileName.exists() && tintSerFile == null) {
            tintSerFile = defaultTintSerFileName;
        }

        if (tintSerFile != null) {
            loadTint = tintSerFile.exists();
        }

        if (loadTint) {
            logger.info("Loading SER file");
            FileInputStream fis = new FileInputStream(tintSerFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            tintAnnotations = (Map<String, Map<Integer, Annotation>>) ois.readObject();
            ois.close();
        } else {
            logger.info("Starting Tint");
            Properties properties = new Properties();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("tint.properties");
            properties.load(inputStream);

            pipeline = new TintPipeline();
            pipeline.addProperties(properties);
            pipeline.load();
        }

        Map<String, List<Datum<String, String>>> allDatum = new HashMap<>();

        int documentID = 0;
        for (MainDocument document : documents) {
            documentID++;

            List<RelationPair> rawRelations = document.getRelations();
            TreeMap<Integer, NERSpan> ners = document.getNerSpanMap();
            TreeMap<Integer, TreeMap<Integer, String>> sentences = document.getSentences();

//            System.out.println(sentences.get(14));
//            for (Integer key : ners.keySet()) {
//                System.out.println(key + " --- " + ners.get(key));
//            }
//            for (RelationPair rawRelation : rawRelations) {
//                System.out.println(rawRelation);
//            }
//            System.exit(1);

            HashMultimap<Integer, RelationPair> relations = HashMultimap.create();

            // Check whether the relation spans more than one sentence
            TreeSet<Integer> ignoreRelations = new TreeSet<>();
            for (int i = 0, relationsSize = rawRelations.size(); i < relationsSize; i++) {
                RelationPair relation = rawRelations.get(i);
                Integer s1 = ners.get(relation.getEntity1()).getSentence();
                Integer s2 = ners.get(relation.getEntity2()).getSentence();
                if (!s1.equals(s2)) {
                    ignoreRelations.add(i);
                    System.err.println("Relation " + relation.getLabel() + " spans two differente sentences");
                    continue;
                }
                relations.put(s1, relation);
            }

            for (Integer relationIndex : ignoreRelations.descendingSet()) {
                rawRelations.remove(relationIndex.intValue());
            }

            logger.info(document.getFileName());
            if (skipFiles.contains(document.getFileName())) {
                continue;
            }
            allDatum.put(document.getFileName(), new ArrayList<>());

            for (Integer key : sentences.keySet()) {
                TreeMap<Integer, String> sentence = sentences.get(key);

                List<String> theseTintTokens = new ArrayList<>();
                List<String> theseTintNers = new ArrayList<>();

                StringBuilder builder = new StringBuilder();
                for (String token : sentence.values()) {
                    builder.append(token.replaceAll("\\s+", "_"));
                    builder.append(" ");
                }
                String sentenceText = builder.toString().trim();
                Annotation tintAnnotation;
                if (loadTint) {
                    tintAnnotation = tintAnnotations.get(document.getFileName()).get(key);
                } else {
                    tintAnnotation = pipeline.runRaw(sentenceText);
                    tintAnnotations.putIfAbsent(document.getFileName(), new HashMap<>());
                    tintAnnotations.get(document.getFileName()).put(key, tintAnnotation);
                }
                assert tintAnnotation.get(CoreAnnotations.SentencesAnnotation.class).size() == 1;
                CoreMap sentenceAnnotation = tintAnnotation.get(CoreAnnotations.SentencesAnnotation.class).get(0);
                List<CoreLabel> tokenList = sentenceAnnotation.get(CoreAnnotations.TokensAnnotation.class);

                Map<Integer, String> thisNerMap = new HashMap<>();
                for (NERSpan ner : ners.values()) {
                    if (!ner.getSentence().equals(key)) {
                        continue;
                    }
                    for (Integer tokenID : ner.getTokens()) {
                        thisNerMap.put(tokenID, ner.getLabel());
                    }
                }

                List<String> thisNers = new ArrayList<>();
                Map<Integer, Integer> tokenMap = new HashMap<>();
                int i = 0;
                for (Integer tokenIndex : sentence.keySet()) {
                    thisNers.add(thisNerMap.getOrDefault(tokenIndex, "O"));
                    tokenMap.put(tokenIndex, i);
                    i++;
                }

                List<Datum<String, String>> datumList = EnteAnnotator.collectFeatures(document.getFileName() + "-" + key, sentenceAnnotation, thisNers);
                allDatum.get(document.getFileName()).addAll(datumList);

                int nerIndex = -1;
                HashMultimap<Integer, Integer> indexConversion = HashMultimap.create();
                Map<Integer, String> nerLabels = new HashMap<>();
                Map<Integer, Integer> nerEnds = new HashMap<>();
                String lastNer = "O";
                int lastIndex = -1;
                for (int j = 0, getSize = tokenList.size(); j < getSize; j++) {
                    CoreLabel token = tokenList.get(j);
                    String ner = token.ner();

                    if (ner == null) {
                        ner = "O";
                    }

                    if (useSplit) {
                        if (token.isMWT()) {
                            theseTintTokens.add(token.word());
                        } else {
                            theseTintTokens.add(token.originalText());
                        }
                        if (!token.isMWT() || (token.isMWT() && token.isMWTFirst())) {
                            nerIndex++;
                        }
                    } else {
                        if (!token.isMWT() || (token.isMWT() && token.isMWTFirst())) {
                            if (allUpper.matcher(token.originalText()).matches() && fixCase) {
                                theseTintTokens.add(token.word());
                            } else {
                                theseTintTokens.add(token.originalText());
                            }
                            nerIndex++;
                        }
                    }

                    // Check whether Tint annotation is consistent with
                    // the manual annotation on entities not in PER/LOC/ORG
                    try {
                        switch (thisNers.get(nerIndex)) {
                            case "EMAIL":
                                assert thisNers.get(nerIndex).equals(ner);
                                break;
                            case "DATE":
                                assert thisNers.get(nerIndex).equals(ner) || ner.equals("NUMBER");
                                break;
                            case "TEL":
                            case "FAX":
                            case "DOCID":
                                assert ner.equals("NUMBER");
                                break;
                            case "CF":
                                assert ner.equals("NUMBER") || ner.equals("CF");
                                break;
                            case "VAT":
                                assert ner.equals("NUMBER") || ner.equals("VAT");
                                break;
                        }
                    } catch (Exception e) {
                        logger.info(document.getFileName());
                        logger.info(sentence.toString());
                        throw new Exception(e);
                    }

                    indexConversion.put(nerIndex, j);

                    // Replace guessed NER tags with gold
                    String goldNer = nerReplace.get(thisNers.get(nerIndex));
                    if (goldNer == null) {
                        goldNer = "O";
                    }

//                    System.out.println(token.word() + " --- " + ner + " --- " + goldNer + " --- " + thisNers.get(nerIndex));

                    boolean nerReplaceWithGold = nerReplace.containsKey(ner) ||
                            nerReplace.containsKey(thisNers.get(nerIndex)) ||
                            (ner.equals("O") && !goldNer.equals("O"));
//                    if (nerReplace.containsKey(ner) || nerReplace.containsKey(thisNers.get(nerIndex))) {
                    if (nerReplaceWithGold) {
                        token.set(CoreAnnotations.NamedEntityTagAnnotation.class, goldNer);
                        theseTintNers.add(goldNer);
                        ner = goldNer;
                    } else {
                        theseTintNers.add("O");
                    }

                    if (!ner.equals("O")) {
                        if (!ner.equals(lastNer)) {
                            nerLabels.put(j, ner);
                            nerEnds.put(j, j);
                            lastIndex = j;
                        } else {
                            nerEnds.put(lastIndex, j);
                        }
                    }

                    lastNer = ner;
                }

//                System.out.println(theseTintTokens.size());
//                System.out.println(theseTintTokens);
//                System.out.println(theseTintNers.size());
//                System.out.println(theseTintNers);
//                System.out.println(thisNers.size());
//                System.out.println(thisNers);
//                System.out.println(indexConversion);
//                System.out.println(nerLabels);
//                System.out.println(nerEnds);

                tintTokens.add(theseTintTokens);
                if (useSplit) {
                    tintNers.add(theseTintNers);
                } else {
                    tintNers.add(thisNers);
                }

                Map<Integer, EntityMention> entityIndex = new TreeMap<>();
                for (Integer startKey : nerLabels.keySet()) {
                    String nerTag = nerLabels.get(startKey);
                    Integer nerEnd = nerEnds.get(startKey);
                    String identifier = "entity-" + nerTag + "-" + documentID + "-" + key + "-" + startKey;
                    Span extentSpan = new Span(startKey, nerEnd + 1);
                    EntityMention entity = new EntityMention(identifier, sentenceAnnotation, extentSpan, extentSpan, nerTag, null, null);
                    if (saveRawEntityList != null && nerReplace.containsKey(nerTag)) {
                        entityWriter.write(String.format("%s - %s - %d - %s\n", nerTag, document.getFileName(), startKey, entity.getValue()));
                    }
                    entity.setHeadTokenPosition(startKey);
                    AnnotationUtils.addEntityMention(sentenceAnnotation, entity);
                    for (int j = startKey; j <= nerEnd; j++) {
                        entityIndex.put(j, entity);
                    }
                }

                for (RelationPair relationPair : relations.get(key)) {
                    String type = relationPair.getLabel();
                    List<ExtractionObject> args = new ArrayList<>();
                    List<Integer> tokens1 = ners.get(relationPair.getEntity1()).getTokens();
                    List<Integer> tokens2 = ners.get(relationPair.getEntity2()).getTokens();
                    Integer minEntity1 = getMin(indexConversion, tokenMap, tokens1);
                    Integer minEntity2 = getMin(indexConversion, tokenMap, tokens2);
                    EntityMention entity1 = entityIndex.get(minEntity1);
                    EntityMention entity2 = entityIndex.get(minEntity2);
                    args.add(entity1);
                    args.add(entity2);
                    try {
                        Span span = new Span(entity1.getExtentTokenStart(), entity2.getExtentTokenEnd());
                        String identifier = RelationMention.makeUniqueId();
                        RelationMention relationMention = new RelationMention(identifier, sentenceAnnotation, span, type, null, args);
                        AnnotationUtils.addRelationMention(sentenceAnnotation, relationMention);
                    } catch (Exception e) {
                        logger.warning("Error in file " + document.getFileName());
//                        System.out.println("Keyset: " + entityIndex);
//                        for (Integer k : entityIndex.keySet()) {
//                            System.out.println(k + " --- " + entityIndex.get(k).getType());
//                        }
                        List<String> tintNerList = new ArrayList<>();
                        for (CoreLabel token : tintAnnotation.get(CoreAnnotations.TokensAnnotation.class)) {
                            tintNerList.add(token.ner());
                        }

                        for (int index = 0, thisNersSize = thisNers.size(); index < thisNersSize; index++) {
                            String thisNer = thisNers.get(index);
                            for (Integer newIndex : indexConversion.get(index)) {
                                System.out.println(index + " --- " + thisNer + " --- " + newIndex + " --- " + tintNerList.get(newIndex));
                            }
                        }

                        System.out.println(indexConversion);

                        System.out.println(relationPair);
                        System.out.println(entityIndex.keySet());
                        System.out.println();
                        System.out.println(entity1);
                        System.out.println(minEntity1);
                        System.out.println(tokens1);
                        System.out.println(relationPair.getEntity1());
                        System.out.println(entity2);
                        System.out.println(minEntity2);
                        System.out.println(tokens2);
                        System.out.println(relationPair.getEntity2());
                        System.out.println();
                        System.out.println(theseTintTokens);
                        System.out.println(thisNers);
                        System.out.println(ANSI_GREEN + thisNers.size());
                        System.out.println(ANSI_RESET + relationPair);
//                        logger.info(theseTintTokens.get(indexConversion.get(relation.getMainTokenID()).stream().findFirst().get()));
//                        logger.info(theseTintTokens.get(indexConversion.get(relation.getSlaveTokenID()).stream().findFirst().get()));
                        System.out.println(tintNerList);
                        System.out.println(ANSI_GREEN + tintNerList.size());
//                        logger.info(entityIndex.toString());
//                        logger.info(entity1.toString());
//                        logger.info(entity2.toString());
                        e.printStackTrace();
                        System.exit(1);
                    }
                }

                AnnotationUtils.addSentence(retAnnotation, sentenceAnnotation);
            }
        }

        if (performCrossValidation) {
            int tpCount = 0;
            int fpCount = 0;
            int fnCount = 0;

            int partitionCount = 10;
            List<Dataset<String, String>> trainDatasets = new ArrayList<>(partitionCount);
            List<Dataset<String, String>> testDatasets = new ArrayList<>(partitionCount);

            for (int i = 0; i < partitionCount; i++) {
                trainDatasets.add(new Dataset<>());
                testDatasets.add(new Dataset<>());
            }

            int index = 0;
            for (String object : allDatum.keySet()) {
                int thisIndex = index++ % partitionCount;
                testDatasets.get(thisIndex).addAll(allDatum.get(object));
                for (int i = 0; i < partitionCount; i++) {
                    if (i == thisIndex) {
                        continue;
                    }
                    trainDatasets.get(i).addAll(allDatum.get(object));
                }
            }

            for (int i = 0; i < partitionCount; i++) {
                logger.info("Training partition #" + (i + 1));
                LinearClassifierFactory<String, String> lcFactory = new LinearClassifierFactory<>(1e-4, false, 1.0);
                lcFactory.setVerbose(true);
                LinearClassifier<String, String> classifier = lcFactory.trainClassifier(trainDatasets.get(i));
                PrecisionRecallStats precisionRecallStats = new PrecisionRecallStats(classifier, testDatasets.get(i), "ORG");
                StringBuilder builder = new StringBuilder();
                builder.append("Results of partition #").append(i + 1).append("\n");
                builder.append("Precision: ").append(precisionRecallStats.getPrecisionDescription(2)).append("\n");
                builder.append("Recall: ").append(precisionRecallStats.getRecallDescription(2)).append("\n");
                builder.append("F1: ").append(precisionRecallStats.getF1Description(2)).append("\n");

                logger.info(builder.toString().trim());

                tpCount += precisionRecallStats.getTP();
                fnCount += precisionRecallStats.getFN();
                fpCount += precisionRecallStats.getFP();
            }

            PrecisionRecallStats globalStats = new PrecisionRecallStats(tpCount, fpCount, fnCount);
            StringBuilder builder = new StringBuilder();
            builder.append("Global results").append("\n");
            builder.append("Precision: ").append(globalStats.getPrecisionDescription(3)).append("\n");
            builder.append("Recall: ").append(globalStats.getRecallDescription(3)).append("\n");
            builder.append("F1: ").append(globalStats.getF1Description(3)).append("\n");
            logger.info(builder.toString().trim());

        }

        if (enteModelFile != null) {

            logger.info("Training classifier");
            GeneralDataset<String, String> dataset = new RVFDataset<>();
            for (String filename : allDatum.keySet()) {
                List<Datum<String, String>> datumList = allDatum.get(filename);
                dataset.addAll(datumList);

            }
            LinearClassifierFactory<String, String> lcFactory = new LinearClassifierFactory<>(1e-4, false, 1.0);
            LinearClassifier<String, String> classifier = lcFactory.trainClassifier(dataset);
            LinearClassifier.writeClassifier(classifier, enteModelFile.getAbsolutePath());
        }

        if (tintSerFile != null && !loadTint) {
            logger.info("Writing SER file");
            FileOutputStream fos = new FileOutputStream(tintSerFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(tintAnnotations);
            oos.close();
        }

        if (saveRawEntityList != null) {
            entityWriter.close();
        }

        if (saveRawNerFile != null) {
            logger.info("Writing plain NER file");

            BufferedWriter writer = new BufferedWriter(new FileWriter(saveRawNerFile));

            for (int i = 0, tintTokensSize = tintTokens.size(); i < tintTokensSize; i++) {
                List<String> tintSentence = tintTokens.get(i);
                List<String> tintNer = tintNers.get(i);

                for (int j = 0, tintSentenceSize = tintSentence.size(); j < tintSentenceSize; j++) {
                    String token = tintSentence.get(j);
                    String ner = tintNer.get(j);
                    if (!nerReplace.containsKey(ner)) {
                        ner = "O";
                    }
                    writer.append(token.replaceAll("\\s+", "_")).append("\t").append(ner).append("\n");
                }
                writer.append("\n");
            }

//            for (CoreMap sentence : retAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
//                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
//                    String ner = token.ner();
//                    if (!nerReplace.contains(ner)) {
//                        ner = "O";
//                    }
//                    writer.append(token.word()).append("\t").append(ner).append("\n");
//                }
//                writer.append("\n");
//            }

            writer.close();
        }

        return retAnnotation;
    }

    private Integer getMin(HashMultimap<Integer, Integer> indexConversion, Map<Integer, Integer> tokenMap, List<Integer> tokens) {
        Set<Integer> allInts = new HashSet<>();
        for (Integer token : tokens) {
            allInts.addAll(indexConversion.get(tokenMap.get(token)));
//            System.out.println("Path: " + token + " --> " + tokenMap.get(token) + " --> " + indexConversion.get(tokenMap.get(token)));
        }

        return Collections.min(allInts);
    }

    public static void main(String[] args) {

        try {
            final CommandLine cmd = CommandLine
                    .parser()
                    .withName("./kind-relations-reader")
                    .withHeader(
                            "Run Relation extractor and training")
                    .withOption("i", "input", "Input folder", "FILE", CommandLine.Type.DIRECTORY_EXISTING, true, false, true)
                    .withOption("o", "output-ner", "Output NER file", "FILE", CommandLine.Type.FILE, true, false, false)
                    .withOption("e", "output-entities", "Output entities file", "FILE", CommandLine.Type.FILE, true, false, false)
                    .withOption("w", "output-webanno", "Output WebAnno folder", "FOLDER", CommandLine.Type.DIRECTORY, true, false, false)
                    .withOption("t", "output-tint-ser", "Output Tint ser file", "FILE", CommandLine.Type.FILE, true, false, false)
                    .withOption("n", "output-ente-ser", "Output ENTE model ser file", "FILE", CommandLine.Type.FILE, true, false, false)
                    .withOption("c", "ente-cross-validation", "Performs cross-validation for ENTE classifier")
                    .withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

            File inputFolder = cmd.getOptionValue("input", File.class);
            File outputNer = cmd.getOptionValue("output-ner", File.class);
            File outputEntities = cmd.getOptionValue("output-entities", File.class);
            File outputWebanno = cmd.getOptionValue("output-webanno", File.class);
            File outputTintSer = cmd.getOptionValue("output-tint-ser", File.class);
            File enteModelSer = cmd.getOptionValue("output-ente-ser", File.class);

            KindRelationsReader_backup reader = new KindRelationsReader_backup();
            reader.setSaveRawNerFile(outputNer);
            reader.setSaveRawEntityList(outputEntities);
            reader.setSaveWebAnnoRelationFiles(outputWebanno);
            reader.setTintSerFile(outputTintSer);
            reader.setEnteModelFile(enteModelSer);
            reader.setPerformCrossValidation(cmd.hasOption("ente-cross-validation"));

            reader.setLoggerLevel(Level.INFO);
            reader.setFixCase(false);
            reader.setParseTint(true);

            try {
                reader.parse(inputFolder.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            CommandLine.fail(e);
        }
//        if (args.length < 4) {
//            System.err.println("Arguments: <inputFolder> <outputFile> <outputEntities> <webannoDir>");
//            System.exit(1);
//        }
//
//        String inputFolder = args[0];
//        String outputNerFile = args[1];
//        String outputEntitiesFile = args[2];
//        String webAnnoDir = args[3];

    }
}
