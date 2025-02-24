package eu.fbk.dh.utils.wemapp.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TsvToWebAnno {
    public static void main(String[] args) {
//        String inputFile = "/Users/alessio/Dropbox/relation-extraction/all-wiki-split.tsv";
//        String outputFile = "/Users/alessio/Dropbox/relation-extraction/all-wiki-split.webanno.tsv";
//        convert(inputFile, outputFile);

        String inputFolder = "/Users/alessio/Downloads/annotazioni/annotazioni/wiki-out-nosplit";
        String outputFolder = "/Users/alessio/Downloads/annotazioni/annotazioni/wiki-out-nosplit-webanno";

        File outputFolderFile = new File(outputFolder);
        if (!outputFolderFile.exists()) {
            outputFolderFile.mkdirs();
        }

        try {
            Files.walk(Paths.get(inputFolder))
                    .filter(Objects::nonNull)
                    .filter(Files::isRegularFile)
                    .filter(c -> c.getFileName().toString().substring(c.getFileName().toString().length() - 5).contains(".tsv"))
                    .forEach(x -> {
                        String fileName = x.getFileName().toString();
                        String outputFile = outputFolder + File.separator + fileName;
                        convert(x.toFile().getAbsolutePath(), outputFile);
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void convert(String inputFile, String outputFile) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            StringBuffer buffer = new StringBuffer();

            buffer.append("#FORMAT=WebAnno TSV 3.3\n" +
                    "#T_SP=custom.Span|label\n");
            buffer.append("\n");
            buffer.append("\n");

            String line;

            List<String> tokens = new ArrayList<>();
            List<String> ners = new ArrayList<>();

            int sentenceID = 1;
            int nerID = 0;
            int offset = 0;
            boolean reset = true;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 2 && tokens.size() > 0) {
                    buffer.append("#Text=").append(String.join(" ", tokens)).append("\n");
                    String previousNer = "O";
                    int tokenID = 1;
                    for (int i = 0, tokensSize = tokens.size(); i < tokensSize; i++) {
                        String token = tokens.get(i);
                        String ner = ners.get(i);
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
                        buffer.append(String.format("%d-%d\t%d-%d\t%s\t%s\t_\t_\t\n", sentenceID, tokenID++, offset, offset + token.length(), token, printNer));
                        offset += token.length() + 1;
                        previousNer = ner;
                    }

                    buffer.append("\n");
                    sentenceID++;
                    tokens = new ArrayList<>();
                    ners = new ArrayList<>();
                    continue;
                }

                tokens.add(parts[0]);
                ners.add(parts[1]);
            }

//            System.out.println(buffer.toString().trim());

            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            writer.write(buffer.toString().trim());
            writer.close();

            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
