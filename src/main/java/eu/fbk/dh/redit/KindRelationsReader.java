package eu.fbk.dh.redit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.stats.PrecisionRecallStats;
import edu.stanford.nlp.util.CoreMap;
import eu.fbk.dh.redit.annotators.EnteAnnotator;
import eu.fbk.dh.redit.machinereading.GenericDataSetReader;
import eu.fbk.dh.redit.machinereading.structure.AnnotationUtils;
import eu.fbk.dh.redit.machinereading.structure.EntityMention;
import eu.fbk.dh.redit.machinereading.structure.ExtractionObject;
import eu.fbk.dh.redit.machinereading.structure.RelationMention;
import eu.fbk.dh.redit.machinereading.structure.Span;
import eu.fbk.dh.redit.util.MainDocument;
import eu.fbk.dh.redit.util.NERSpan;
import eu.fbk.dh.redit.util.RelationPair;
import eu.fbk.dh.redit.util.Rule;
import eu.fbk.dh.tint.runner.TintPipeline;
import eu.fbk.utils.core.CommandLine;
import eu.fbk.utils.core.FrequencyHashSet;

public class KindRelationsReader extends GenericDataSetReader {

	private static Map<String, String> relReplace = new HashMap<>();
	private static Map<String, String> nerReplace = new HashMap<>();

	private static File defaultTintSerFileName = new File("tint.ser");

	private static final Pattern allLower = Pattern.compile("[^A-Z]*?[a-z]+[^A-Z]*?");
	private static final Pattern allUpper = Pattern.compile("[^a-z]*?[A-Z]+[^a-z]*?");
	private static final Pattern startUpper = Pattern.compile("[A-Z].*");

	private File saveRawNerFile = null;
	private File saveRawEntityList = null;
	private File tintSerFile = null;
	private File enteModelFile = null;
	private File errorsFile = null;

	private boolean debug = false;
	private boolean exit = false;
	private boolean showRelations = false;

	private static final Pattern nersPattern = Pattern.compile("([A-Za-z0-9]+|\\\\_)(\\[([0-9]+)\\])?");
	private static final Pattern relInfoPattern = Pattern.compile("^([0-9-]+).*");

	private static final Pattern stPattern = Pattern.compile("^([0-9]+)-([0-9]+)$");
	private static final Pattern relPattern = Pattern.compile("^(([0-9]+)-([0-9]+))(\\[([0-9]+)_([0-9]+)\\])?$");

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

	public void setErrorsFile(File errorsFile) {
		this.errorsFile = errorsFile;
	}

	public void setSaveRawEntityList(File saveRawEntityList) {
		this.saveRawEntityList = saveRawEntityList;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public void setShowRelations(boolean showRelations) {
		this.showRelations = showRelations;
	}

	public void setExit(boolean exit) {
		this.exit = exit;
	}

	public void setParseTint(Boolean parseTint) {
		this.parseTint = parseTint;
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

	public static <T> T coalesce(T... t) {
		return Stream.of(t).filter(Objects::nonNull).findFirst().orElse(null);
	}

	public MainDocument loadTsvDocument(Path x, Multimap<String, Rule> tokenErrors) throws Exception {
		String fullFileName = x.toString();
		FileInputStream fileInputStream = new FileInputStream(x.toFile());
		BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8));

//        logger.info(x.getFileName().toString());

		TreeMap<Integer, TreeMap<Integer, String>> sentences = new TreeMap<>();
		TreeMap<Integer, NERSpan> nerSpanMap = new TreeMap<>();
		List<RelationPair> relations = new ArrayList<>();

		Map<String, NERSpan> nerSpanMap_single = new TreeMap<>();
		Map<String, Integer> nerSpanIndexMap = new HashMap<>();

		String rLine;

		// First pass: NERs
		int offset = 0;
		int lastSentenceID = -1;
		Map<Integer, Map<Integer, Integer>> offsetMap = new HashMap<>();
		String prefix = "";

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
			offsetMap.putIfAbsent(sentenceID, new HashMap<>());
			if (lastSentenceID != sentenceID) {
				offset = 0;
				lastSentenceID = sentenceID;
			}
			int tokenID = Integer.parseInt(matcher.group(2));

			sentences.putIfAbsent(sentenceID, new TreeMap<>());
			String tokenText = parts[2];
			if (prefix.length() > 0) {
				tokenText = prefix + tokenText;
				prefix = "";
			}

			String fileName = x.toFile().getName();
			String index = fileName + "-" + sentenceID + "-" + tokenID;
			boolean continueLoop = false;
			if (tokenErrors.containsKey(index)) {
				for (Rule rule : tokenErrors.get(index)) {
					switch (rule.getType()) {
					case "tokenSplit":
						sentences.get(sentenceID).putIfAbsent(tokenID + offset,
								tokenText.substring(0, rule.getOffset()));
						tokenText = tokenText.substring(rule.getOffset());
						offset++;
						break;
					case "tokenMerge":
						continueLoop = true;
						prefix = tokenText;
						offset--;
						break;
					}
				}
			}

			offsetMap.get(sentenceID).put(tokenID, offset);

			if (continueLoop) {
				continue;
			}

			sentences.get(sentenceID).putIfAbsent(tokenID + offset, tokenText);

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
					NERSpan nerSpan = new NERSpan(label, sentenceID, tokenID + offset);
					String nerIndex = sentenceID + "-" + (tokenID + offset);
					nerSpanMap_single.put(nerIndex, nerSpan);
				} else {
					Integer idInt = Integer.parseInt(id);
					nerSpanMap.putIfAbsent(idInt, new NERSpan(label, sentenceID, -1));
					nerSpanMap.get(idInt).addToken(tokenID + offset);
				}
			}
		}

		Integer lastIndex = Collections.max(nerSpanMap.keySet());
		for (String key : nerSpanMap_single.keySet()) {
			lastIndex++;
			nerSpanMap.put(lastIndex, nerSpanMap_single.get(key));
			nerSpanIndexMap.put(key, lastIndex);
		}

		// Second pass: relations
		fileInputStream.getChannel().position(0); // reset the file
		while ((rLine = reader.readLine()) != null) {
			String[] parts = rLine.split("\t");
			rLine = rLine.trim();
			if (rLine.startsWith("#")) {
				continue;
			}
			if (parts.length < 6) {
				continue;
			}

			Matcher matcher = stPattern.matcher(parts[0]);
			if (!matcher.find()) {
				continue;
			}
			int sentenceID = Integer.parseInt(matcher.group(1));
			int tokenID = Integer.parseInt(matcher.group(2));

			String[] relationList = parts[4].split("\\|");
			String[] relationInfoList = parts[5].split("\\|");

			if (relationList.length != relationInfoList.length) {
				throw new Exception("Lists of relations differ in length");
			}

			for (int i = 0, relationListLength = relationList.length; i < relationListLength; i++) {
				String relationName = relationList[i];
				String relationInfo = relationInfoList[i];

//                if (!relationName.contains("_")) {
//                    System.out.println(fullFileName + " - Before: " + relationName);
//                }
				Matcher relMatcher = relPattern.matcher(relationInfo);
				if (!relMatcher.find()) {
					continue;
				}
//                System.out.println(fullFileName + " - After: " + relationName);

//                String stIndex = relMatcher.group(1);
				Integer relSentenceID = Integer.parseInt(relMatcher.group(2));
				Integer relTokenID = Integer.parseInt(relMatcher.group(3));

				Integer otherEntityID = Integer.parseInt(coalesce(relMatcher.group(5), "0"));
				Integer thisEntityID = Integer.parseInt(coalesce(relMatcher.group(6), "0"));

				if (thisEntityID.equals(0)) {
					Integer relOffset = offsetMap.get(sentenceID).get(tokenID);
					String nerIndex = sentenceID + "-" + (tokenID + relOffset);
					thisEntityID = nerSpanIndexMap.get(nerIndex);
				}
				if (otherEntityID.equals(0)) {
					Integer relOffset = offsetMap.get(relSentenceID).get(relTokenID);
					String nerIndex = relSentenceID + "-" + (relTokenID + relOffset);
					otherEntityID = nerSpanIndexMap.get(nerIndex);
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

		relationLoop: for (RelationPair relation : relations) {
			boolean switchEntities = false;
			NERSpan entity1 = nerSpanMap.get(relation.getEntity1());
//            NERSpan entity2 = nerSpanMap.get(relation.getEntity2());
			String relationName = relation.getLabel();

			String firstNer = null;
			try {
				firstNer = entity1.getLabel();
			} catch (Exception e) {
				System.err.println("Span error in relation: " + relationName + " - in file: " + fullFileName);
				if (debug) {
					System.out.println(x.getFileName());
					System.out.println(entitiesToRemove);
					System.out.println(relation);
					System.out.println(nerSpanMap.keySet());
				}
				if (exit) {
					System.exit(1);
				}
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

	}

	private void mergeSentences(TreeMap<Integer, TreeMap<Integer, String>> sentences, Map<Integer, NERSpan> nerSpanMap,
			List<RelationPair> relations, Integer sentence1, Integer sentence2) {

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

	public KindRelationsReader() {
		super(null, false, true, true);
		InputStream resourceAsStream;

		resourceAsStream = getClass().getClassLoader().getResourceAsStream("relationsReplace.tsv");
		if (resourceAsStream == null) {
			throw new IllegalArgumentException("File not found!");
		} else {
			try {
				Iterable<CSVRecord> records = CSVFormat.DEFAULT.withCommentMarker('#').withDelimiter('\t')
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
				Iterable<CSVRecord> records = CSVFormat.DEFAULT.withCommentMarker('#').withDelimiter('\t')
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

		logger = Logger.getLogger(KindRelationsReader.class.getName());
		logger.setLevel(Level.INFO);
	}

	public void setFixCase(Boolean fixCase) {
		this.fixCase = fixCase;
	}

	public void setUseSplit(Boolean useSplit) {
		this.useSplit = useSplit;
	}

	private void populateDocuments(String path, List<MainDocument> documents, Multimap<String, Rule> tokenErrors)
			throws IOException {
		Files.walk(Paths.get(path)).filter(Objects::nonNull).filter(Files::isRegularFile).filter(
				c -> c.getFileName().toString().substring(c.getFileName().toString().length() - 4).contains(".tsv"))
				.forEach(x -> {
					try {
						MainDocument mainDocument = loadTsvDocument(x, tokenErrors);
//                                Document document = convertDocument(mainDocument);
						documents.add(mainDocument);
					} catch (Exception e) {
						e.printStackTrace();
					}
				});

		logger.info(documents.size() + " documents read");
	}

	@Override
	public Annotation read(String path) throws Exception {

		Multimap<String, Rule> tokenErrors = HashMultimap.create();

		if (errorsFile != null) {
			logger.info("Reading error file");
			BufferedReader reader = new BufferedReader(new FileReader(errorsFile));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split("\t");
				if (parts.length < 2) {
					continue;
				}
				String fileName = parts[0];
				String type = parts[1];

				Rule myRule = new Rule(fileName, type);
				switch (type) {
				case "tokenSplit":
				case "tokenMerge":
					int sentence = Integer.parseInt(parts[2]);
					myRule.setSentence(sentence);
					int token = Integer.parseInt(parts[3]);
					myRule.setToken(token);
					if (parts.length > 4) {
						int offset = Integer.parseInt(parts[4]);
						myRule.setOffset(offset);
					}
					String index = fileName + "-" + sentence + "-" + token;
					tokenErrors.put(index, myRule);
					break;
				}

			}
			reader.close();
		}

		logger.info("Reading documents");
		List<MainDocument> documents = new ArrayList<>();
		populateDocuments(path, documents, tokenErrors);

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
				try {
					Integer s1 = ners.get(relation.getEntity1()).getSentence();
					Integer s2 = ners.get(relation.getEntity2()).getSentence();
					if (!s1.equals(s2)) {
						ignoreRelations.add(i);
//                        System.err.println("Relation " + relation.getLabel() + " spans two differente sentences");
						continue;
					}
					relations.put(s1, relation);
				} catch (Exception e) {
					logger.warning("Error in file " + document.getFileName());
					if (debug) {
						System.out.println("Relation: " + relation);
						System.out.println("Entity 1: " + relation.getEntity1());
						System.out.println("Entity 2: " + relation.getEntity2());
						System.out.println("ners: " + ners);
					}
					if (exit) {
						System.exit(1);
					}
				}
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

				List<Datum<String, String>> datumList = EnteAnnotator
						.collectFeatures(document.getFileName() + "-" + key, sentenceAnnotation, thisNers);
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

					boolean nerReplaceWithGold = !goldNer.equals("O") && (nerReplace.containsKey(ner)
							|| nerReplace.containsKey(thisNers.get(nerIndex)) || ner.equals("O"));
//                    if (nerReplace.containsKey(ner) || nerReplace.containsKey(thisNers.get(nerIndex))) {
					if (nerReplaceWithGold) {
						if (debug) {
							System.out.println(
									"Replacing (token: " + token.word() + "): " + token.ner() + " with " + goldNer);
						}
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
					EntityMention entity = new EntityMention(identifier, sentenceAnnotation, extentSpan, extentSpan,
							nerTag, null, null);
					if (saveRawEntityList != null && nerReplace.containsKey(nerTag)) {
						entityWriter.write(String.format("%s - %s - %d - %s\n", nerTag, document.getFileName(),
								startKey, entity.getValue()));
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
					TreeSet<Integer> allTokensEntity1 = getAll(indexConversion, tokenMap, tokens1);
					TreeSet<Integer> allTokensEntity2 = getAll(indexConversion, tokenMap, tokens2);
//                    EntityMention entity1 = null;
//                    for (Integer index : allTokensEntity1) {
//                        if (entityIndex.containsKey(index)) {
//                            entity1 = entityIndex.get(index);
//                        }
//                    }
//                    EntityMention entity2 = null;
//                    for (Integer index : allTokensEntity2) {
//                        if (entityIndex.containsKey(index)) {
//                            entity2 = entityIndex.get(index);
//                        }
//                    }
					EntityMention entity1 = entityIndex.get(Collections.min(allTokensEntity1));
					EntityMention entity2 = entityIndex.get(Collections.min(allTokensEntity2));

					args.add(entity1);
					args.add(entity2);
					try {
						Span span = new Span(entity1.getExtentTokenStart(), entity2.getExtentTokenEnd());
						if (showRelations) {
							System.out.println(type + " --- " + entity1.getValue() + " --- " + entity2.getValue());
						}
						String identifier = RelationMention.makeUniqueId();
						RelationMention relationMention = new RelationMention(identifier, sentenceAnnotation, span,
								type, null, args);
						AnnotationUtils.addRelationMention(sentenceAnnotation, relationMention);
					} catch (Exception e) {
						logger.warning("Error in file " + document.getFileName());

//                        String json = JSONOutputter.jsonPrint(tintAnnotation);
//                        System.out.println(json);
//                        System.out.println(sentenceText);

						List<String> tintNerList = new ArrayList<>();
						for (CoreLabel token : tintAnnotation.get(CoreAnnotations.TokensAnnotation.class)) {
							tintNerList.add(token.ner());
						}

						if (debug) {
							int j = 0;
							for (int index = 0, thisNersSize = thisNers.size(); index < thisNersSize; index++) {
								String thisNer = thisNers.get(index);
								for (Integer newIndex : indexConversion.get(index)) {
									System.out.println(theseTintTokens.get(j) + " --- " + index + " --- " + thisNer
											+ " --- " + newIndex + " --- " + tintNerList.get(newIndex));
									j++;
								}
							}

							System.out.println("Index conversion: " + indexConversion);
							System.out.println("Token map: " + tokenMap);

							System.out.println("Relation pair: " + relationPair);
							System.out.println("entityIndex keySet: " + entityIndex.keySet());

							System.out.println();
							System.out.println("Entity 1: " + entity1);
							System.out.println("Tokens (converted): " + allTokensEntity1);
							System.out.println("Tokens (original): " + tokens1);
							System.out.println("Entity ID: " + relationPair.getEntity1());

							System.out.println();
							System.out.println("Entity 2: " + entity2);
							System.out.println("Tokens (converted): " + allTokensEntity2);
							System.out.println("Tokens (original): " + tokens2);
							System.out.println("Entity ID: " + relationPair.getEntity2());

							System.out.println();
							System.out.println(theseTintTokens);
							System.out.println(thisNers);
							System.out.println(ANSI_GREEN + thisNers.size());
							System.out.println(ANSI_RESET + relationPair);
//                        logger.info(theseTintTokens.get(indexConversion.get(relation.getMainTokenID()).stream().findFirst().get()));
//                        logger.info(theseTintTokens.get(indexConversion.get(relation.getSlaveTokenID()).stream().findFirst().get()));
							System.out.println(ANSI_GREEN + tintNerList.size());
							System.out.println(ANSI_RESET + tintNerList);
//                        String json = JSONOutputter.jsonPrint(tintAnnotation);
//                        System.out.println(json);
//                        logger.info(entityIndex.toString());
//                        logger.info(entity1.toString());
//                        logger.info(entity2.toString());
//                            e.printStackTrace();
						}
						if (exit) {
							System.exit(1);
						}
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
				PrecisionRecallStats precisionRecallStats = new PrecisionRecallStats(classifier, testDatasets.get(i),
						"ORG");
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

	private TreeSet<Integer> getAll(HashMultimap<Integer, Integer> indexConversion, Map<Integer, Integer> tokenMap,
			List<Integer> tokens) {
		TreeSet<Integer> allInts = new TreeSet<>();
		for (Integer token : tokens) {
			allInts.addAll(indexConversion.get(tokenMap.get(token)));
		}

		return allInts;
	}

	public static void main(String[] args) {

		try {
			final CommandLine cmd = CommandLine.parser().withName("./kind-relations-reader")
					.withHeader("Run Relation extractor and training")
					.withOption("i", "input", "Input folder", "FILE", CommandLine.Type.DIRECTORY_EXISTING, true, false,
							true)
					.withOption("r", "errors-file", "Input error file", "FILE", CommandLine.Type.FILE_EXISTING, true,
							false, false)
					.withOption("o", "output-ner", "Output NER file", "FILE", CommandLine.Type.FILE, true, false, false)
					.withOption("e", "output-entities", "Output entities file", "FILE", CommandLine.Type.FILE, true,
							false, false)
					.withOption("t", "output-tint-ser", "Output Tint ser file", "FILE", CommandLine.Type.FILE, true,
							false, false)
					.withOption("n", "output-ente-ser", "Output ENTE model ser file", "FILE", CommandLine.Type.FILE,
							true, false, false)
					.withOption("c", "ente-cross-validation", "Performs cross-validation for ENTE classifier")
					.withOption("d", "debug-mode", "Enable debug mode").withOption("x", "exit-mode", "Enable exit mode")
					.withLogger(LoggerFactory.getLogger("eu.fbk")).parse(args);

			File inputFolder = cmd.getOptionValue("input", File.class);
			File outputNer = cmd.getOptionValue("output-ner", File.class);
			File outputEntities = cmd.getOptionValue("output-entities", File.class);
			File outputTintSer = cmd.getOptionValue("output-tint-ser", File.class);
			File enteModelSer = cmd.getOptionValue("output-ente-ser", File.class);
			File errorsFile = cmd.getOptionValue("errors-file", File.class);

			KindRelationsReader reader = new KindRelationsReader();
			reader.setSaveRawNerFile(outputNer);
			reader.setErrorsFile(errorsFile);
			reader.setSaveRawEntityList(outputEntities);
			reader.setTintSerFile(outputTintSer);
			reader.setEnteModelFile(enteModelSer);
			reader.setPerformCrossValidation(cmd.hasOption("ente-cross-validation"));

			reader.setLoggerLevel(Level.INFO);
			reader.setFixCase(false);
			reader.setParseTint(true);

			reader.setDebug(cmd.hasOption("debug-mode"));
			reader.setExit(cmd.hasOption("exit-mode"));
			reader.setShowRelations(false);

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
