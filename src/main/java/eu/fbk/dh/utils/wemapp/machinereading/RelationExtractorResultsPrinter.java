package eu.fbk.dh.utils.wemapp.machinereading;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import eu.fbk.dh.utils.wemapp.machinereading.structure.AnnotationUtils;
import eu.fbk.dh.utils.wemapp.machinereading.structure.RelationMention;
import eu.fbk.dh.utils.wemapp.machinereading.structure.RelationMentionFactory;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;

public class RelationExtractorResultsPrinter extends ResultsPrinter {

    protected boolean createUnrelatedRelations;

    protected final RelationMentionFactory relationMentionFactory;

    public RelationExtractorResultsPrinter(RelationMentionFactory factory) {
        this(factory, true);
    }

    public RelationExtractorResultsPrinter() {
        this(new RelationMentionFactory(), true);
    }

    public RelationExtractorResultsPrinter(boolean createUnrelatedRelations) {
        this(new RelationMentionFactory(), createUnrelatedRelations);
    }

    public RelationExtractorResultsPrinter(RelationMentionFactory factory, boolean createUnrelatedRelations) {
        this.createUnrelatedRelations = createUnrelatedRelations;
        this.relationMentionFactory = factory;
    }

    private static final int MAX_LABEL_LENGTH = 31;

    @Override
    public void printResults(PrintWriter pw,
                             List<CoreMap> goldStandard,
                             List<CoreMap> extractorOutput) {
        align(goldStandard, extractorOutput);

        // the mention factory cannot be null here
        assert relationMentionFactory != null : "ERROR: RelationExtractorResultsPrinter.relationMentionFactory cannot be null in printResults!";

        // Count predicted-actual relation type pairs
        Counter<Pair<String, String>> results = new ClassicCounter<>();
        ClassicCounter<String> labelCount = new ClassicCounter<>();

        // TODO: assumes binary relations
        for (int goldSentenceIndex = 0; goldSentenceIndex < goldStandard.size(); goldSentenceIndex++) {
            for (RelationMention goldRelation : AnnotationUtils.getAllRelations(relationMentionFactory, goldStandard.get(goldSentenceIndex), createUnrelatedRelations)) {
                CoreMap extractorSentence = extractorOutput.get(goldSentenceIndex);
                List<RelationMention> extractorRelations = AnnotationUtils.getRelations(relationMentionFactory, extractorSentence, goldRelation.getArg(0), goldRelation.getArg(1));
                labelCount.incrementCount(goldRelation.getType());
                for (RelationMention extractorRelation : extractorRelations) {
                    results.incrementCount(new Pair<>(extractorRelation.getType(), goldRelation.getType()));
                }
            }
        }

        printResultsInternal(pw, results, labelCount);
    }

    private void printResultsInternal(PrintWriter pw, Counter<Pair<String, String>> results, ClassicCounter<String> labelCount) {
        ClassicCounter<String> correct = new ClassicCounter<>();
        ClassicCounter<String> predictionCount = new ClassicCounter<>();
        boolean countGoldLabels = false;
        if (labelCount == null) {
            labelCount = new ClassicCounter<>();
            countGoldLabels = true;
        }

        for (Pair<String, String> predictedActual : results.keySet()) {
            String predicted = predictedActual.first;
            String actual = predictedActual.second;
            if (predicted.equals(actual)) {
                correct.incrementCount(actual, results.getCount(predictedActual));
            }
            predictionCount.incrementCount(predicted, results.getCount(predictedActual));
            if (countGoldLabels) {
                labelCount.incrementCount(actual, results.getCount(predictedActual));
            }
        }

        DecimalFormat formatter = new DecimalFormat();
        formatter.setMaximumFractionDigits(1);
        formatter.setMinimumFractionDigits(1);

        double totalCount = 0;
        double totalCorrect = 0;
        double totalPredicted = 0;

        double totalP = 0;
        double totalR = 0;
        double classCount = 0;

        pw.println("Label\tCorrect\tPredict\tActual\tPrecn\tRecall\tF");
        List<String> labels = new ArrayList<>(labelCount.keySet());
        Collections.sort(labels);

        Set<String> skipLabels = new HashSet<>();
        skipLabels.add("relative");
        skipLabels.add("deathDate");
        skipLabels.add("deathLoc");
        skipLabels.add("docID");
        skipLabels.add("docExpDate");
        skipLabels.add("docIssueDate");
        skipLabels.add("docIssueLoc");
        skipLabels.add("docType");

        for (String label : labels) {
            if (skipLabels.contains(label)) {
                continue;
            }
            double numcorrect = correct.getCount(label);
            double predicted = predictionCount.getCount(label);
            double trueCount = labelCount.getCount(label);
            double precision = (predicted > 0) ? (numcorrect / predicted) : 0;
            double recall = numcorrect / trueCount;
            double f = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;
            pw.println(StringUtils.padOrTrim(label, MAX_LABEL_LENGTH) + "\t" + numcorrect + "\t" + predicted + "\t" + trueCount + "\t"
                    + formatter.format(precision * 100) + "\t" + formatter.format(100 * recall) + "\t"
                    + formatter.format(100 * f));
            if (!RelationMention.isUnrelatedLabel(label)) {
                totalCount += trueCount;
                totalCorrect += numcorrect;
                totalPredicted += predicted;

                totalP += precision;
                totalR += recall;
                classCount++;
            }
        }

        // Micro
        double precision = (totalPredicted > 0) ? (totalCorrect / totalPredicted) : 0;
        double recall = totalCorrect / totalCount;
        double f = (totalPredicted > 0 && totalCorrect > 0) ? 2 * precision * recall / (precision + recall) : 0.0;
        pw.println("Total (micro)\t" + totalCorrect + "\t" + totalPredicted + "\t" + totalCount + "\t"
                + formatter.format(100 * precision) + "\t" + formatter.format(100 * recall) + "\t" + formatter.format(100 * f));

        // Macro
        double precisionM = totalP / classCount;
        double recallM = totalR / classCount;
        double fM = (totalPredicted > 0 && totalCorrect > 0) ? 2 * precisionM * recallM / (precisionM + recallM) : 0.0;
        pw.println("Total (macro)\t_\t_\t_\t"
                + formatter.format(100 * precisionM) + "\t" + formatter.format(100 * recallM) + "\t" + formatter.format(100 * fM));
    }

    public void printResultsUsingLabels(PrintWriter pw,
                                        List<String> goldStandard,
                                        List<String> extractorOutput) {

        // Count predicted-actual relation type pairs
        Counter<Pair<String, String>> results = new ClassicCounter<>();
        assert (goldStandard.size() == extractorOutput.size());
        for (int i = 0; i < goldStandard.size(); i++)
            results.incrementCount(new Pair<>(extractorOutput.get(i), goldStandard.get(i)));

        printResultsInternal(pw, results, null);
    }
}
