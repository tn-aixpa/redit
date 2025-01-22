package eu.fbk.dh.redit.annotators;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;
import eu.fbk.dh.redit.machinereading.BasicRelationExtractor;
import eu.fbk.dh.redit.machinereading.Extractor;
import eu.fbk.dh.redit.machinereading.MachineReading;
import eu.fbk.dh.redit.machinereading.domains.roth.RothCONLL04Reader;
import eu.fbk.dh.redit.machinereading.domains.roth.RothEntityExtractor;
import eu.fbk.dh.redit.machinereading.structure.EntityMention;
import eu.fbk.dh.redit.machinereading.structure.MachineReadingAnnotations;
import eu.fbk.dh.redit.machinereading.structure.MachineReadingAnnotations.RelationMentionsAnnotation;
import eu.fbk.dh.redit.machinereading.structure.RelationMention;

/**
 * Annotating relations between entities produced by the NER system.
 *
 * @author Sonal Gupta (sonalg@stanford.edu)
 */

public class RelationExtractorAnnotator implements Annotator {

	/**
	 * A logger for this class
	 */
	private static Redwood.RedwoodChannels log = Redwood.channels(RelationExtractorAnnotator.class);
	MachineReading mr;
	private static boolean verbose = false;

	static boolean getVerbose(Properties props) {
		// we keep the old parameter for backwards compatibility
		if (props.containsKey("sup.relation.verbose")) {
			log.warning("sup.relation.verbose is DEPRECATED.  use relation.verbose instead");
			return Boolean.parseBoolean(props.getProperty("sup.relation.verbose"));
		} else {
			return Boolean.parseBoolean(props.getProperty("relation.verbose", "false"));
		}
	}

	public RelationExtractorAnnotator(String annotatorName, Properties props) {
		verbose = getVerbose(props);
		final String relationModel = props.getProperty(annotatorName + ".model");

		try {
			Extractor entityExtractor = new RothEntityExtractor();
			BasicRelationExtractor relationExtractor = BasicRelationExtractor.load(relationModel);

			log.info("Loading relation model from " + relationModel);
			mr = MachineReading.makeMachineReadingForAnnotation(new RothCONLL04Reader(), entityExtractor,
					relationExtractor, null, null, null, true, verbose);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public void annotate(Annotation annotation) {
		// extract entities and relations
		Annotation output = mr.annotate(annotation);

		// transfer entities/relations back to the original annotation
		List<CoreMap> outputSentences = output.get(SentencesAnnotation.class);
		List<CoreMap> origSentences = annotation.get(SentencesAnnotation.class);
		for (int i = 0; i < outputSentences.size(); i++) {
			CoreMap outSent = outputSentences.get(i);
			CoreMap origSent = origSentences.get(i);
			// set entities
			List<EntityMention> entities = outSent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
			origSent.set(MachineReadingAnnotations.EntityMentionsAnnotation.class, entities);
			if (verbose && entities != null) {
				log.info("Extracted the following entities:");
				for (EntityMention e : entities) {
					log.info("\t" + e);
				}
			}

			// set relations
			List<RelationMention> relations = outSent.get(RelationMentionsAnnotation.class);
			origSent.set(RelationMentionsAnnotation.class, relations);
			if (verbose && relations != null) {
				log.info("Extracted the following relations:");
				for (RelationMention r : relations) {
					if (!r.getType().equals(RelationMention.UNRELATED)) {
						log.info(r);
					}
				}
			}

		}
	}

	@Override
	public Set<Class<? extends CoreAnnotation>> requires() {
		return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(CoreAnnotations.TokensAnnotation.class,
				SentencesAnnotation.class, CoreAnnotations.PartOfSpeechAnnotation.class,
				CoreAnnotations.NamedEntityTagAnnotation.class, TreeCoreAnnotations.TreeAnnotation.class,
				SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
				SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
				SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class)));
	}

	@Override
	public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
		return Collections.unmodifiableSet(new ArraySet<>(Arrays
				.asList(MachineReadingAnnotations.EntityMentionsAnnotation.class, RelationMentionsAnnotation.class)));
	}

	public static void main(String[] args) {
		try {
			Properties props = StringUtils.argsToProperties(args);
			props.setProperty("annotators", "tokenize,ssplit,lemma,pos,parse,ner");
			StanfordCoreNLP pipeline = new StanfordCoreNLP();
			String sentence = "Barack Obama lives in America. Obama works for the Federal Goverment.";
			Annotation doc = new Annotation(sentence);
			pipeline.annotate(doc);
			RelationExtractorAnnotator r = new RelationExtractorAnnotator("relation", props);
			r.annotate(doc);
			for (CoreMap s : doc.get(SentencesAnnotation.class)) {
				System.out.println("For sentence " + s.get(CoreAnnotations.TextAnnotation.class));
				List<RelationMention> rls = s.get(RelationMentionsAnnotation.class);
				for (RelationMention rl : rls) {
					System.out.println(rl.toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
