package eu.fbk.dh.redit.annotators;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;

public class AllUpperReplacerAnnotator implements Annotator {

	private static final Pattern allUpper = Pattern.compile("[^a-z]*?[A-Z]+[^a-z]*?");

	@Override
	public void annotate(Annotation annotation) {
		List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
		for (CoreLabel token : tokens) {
			if (allUpper.matcher(token.originalText()).matches()) {
				token.set(CoreAnnotations.TextAnnotation.class,
						token.get(CoreAnnotations.TrueCaseTextAnnotation.class));
				token.set(CoreAnnotations.ValueAnnotation.class,
						token.get(CoreAnnotations.TrueCaseTextAnnotation.class));
			}
		}

	}

	@Override
	public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
		return Collections.emptySet();
	}

	@Override
	public Set<Class<? extends CoreAnnotation>> requires() {
		return Collections.unmodifiableSet(new ArraySet<>(
				Arrays.asList(CoreAnnotations.TokensAnnotation.class, CoreAnnotations.TrueCaseTextAnnotation.class)));
	}
}
