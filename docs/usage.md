# Redit

The tool is packaged as a container. In order to install it, see the following instructions

## Installation

### Requirements
- docker > 1.18
- docker-compose > 1.25.0

### Steps
- Clone the project source
  ```
   git clone https://github.com/tn-aixpa/redit.git
  ```

- Run from project root
  ```
   docker compose up
   ```
  It takes a while to load the pipeline with rules (normalization, sentence splitting etc) and extraction model.

  ```
  redit-frontend  | [Thu Apr 10 08:47:50.677906 2025] [core:notice] [pid 1:tid 1] AH00094: Command line: 'httpd -D FOREGROUND'
  redit           | [main] INFO  eu.fbk.dh.tint.runner.TintServer  - starting 0.0.0.0     8015 (Thu Apr 10 08:47:50 GMT 2025)...
  redit           | [main] INFO  eu.fbk.dh.tint.tokenizer.ItalianTokenizer  - Loaded 37 normalization rules
  redit           | [main] INFO  eu.fbk.dh.tint.tokenizer.ItalianTokenizer  - Loaded 3 sentence splitting rules
  redit           | [main] INFO  eu.fbk.dh.tint.tokenizer.ItalianTokenizer  - Loaded 2 newline chars
  redit           | [main] INFO  eu.fbk.dh.tint.tokenizer.ItalianTokenizer  - Loaded 6 token splitting rules
  redit           | [main] INFO  eu.fbk.dh.tint.tokenizer.ItalianTokenizer  - Loaded 20 regular expressions
  redit           | [main] INFO  eu.fbk.dh.tint.tokenizer.ItalianTokenizer  - Loaded 353 abbreviations
  redit           | [main] INFO  eu.fbk.dh.tint.digimorph.annotator.GuessModelInstance  - Loading guess model for lemma
  redit           | [main] INFO  eu.fbk.dh.utils.wemapp.annotators.RelationExtractorAnnotator  - Loading relation model from relation_model_pipeline.ser
  redit           | [main] INFO  eu.fbk.dh.tint.runner.TintServer  - Pipeline loaded
  redit           | Apr 10, 2025 8:51:26 AM org.glassfish.grizzly.http.server.NetworkListener start
  redit           | INFO: Started listener bound to [0.0.0.0:8015]
  redit           | Apr 10, 2025 8:51:26 AM org.glassfish.grizzly.http.server.HttpServer start
  redit           | INFO: [HttpServer] Started.
  ``` 

- Once the container is up, the interface console is started by default on port 8080 and can be accessed at url
  
  ```
  http://localhost:8080
  ```

## Usage

### Interface
The inteface is available on url 'http://localhost:8080'. The tool is preconfigured with sample input sentences which are used to depict relation extraction. The tool predict attributes and relations for entities in a sentence as can be seen in image below.


 ![image](https://github.com/tn-aixpa/redit/blob/main/assets/1.jpg)



### API

- The tool provides Application programmer interface(API).  The API is available on url
  
  ```
  http://localhost:8080/re-api/tint
  ```

It can be invoked using WGET or CURL as shown in example(CURL) below.


```
..>curl -X POST "http://localhost:8080/re-api/tint" -H 'Content-Type: application/json' 'Accept: application/json' -d "Il sottoscritto Luca Rosetti, nato a Brindisi il 4 maggio 1984 e residente a Sanremo (IM) in Via Matteotti 42 dichiara di essere titolare dell"
```
The predicted relation-extractor output looks like the following

```
{
  "docDate": "2025-04-09",
  "timings": "Annotation pipeline timing information:\nItalianTokenizerAnnotator: 0.0 sec.\nTrueCaseAnnotator: 0.0 sec.\nAllUpperReplacerAnnotator: 0.0 sec.\nPOSTaggerAnnotator: 0.0 sec.\nUPosAnnotator: 0.0 sec.\nSplitterAnnotator: 0.0 sec.\nDigiMorphAnnotator: 0.0 sec.\nDigiLemmaAnnotator: 0.0 sec.\nNERCombinerAnnotator: 0.0 sec.\nTokensRegexAnnotator: 0.0 sec.\nNumeroCivicoAnnotator: 0.0 sec.\nEnteAnnotator: 0.0 sec.\nEntityAnnotator: 0.0 sec.\nParserAnnotator: 0.0 sec.\nDependencyParseAnnotator: 0.0 sec.\nRelationExtractorAnnotator: 0.0 sec.\nTOTAL: 0.0 sec. for 1 tokens at 250.0 tokens/sec.",
  "sentences": [
    {
      "index": 0,
      "characterOffsetBegin": 0,
      "characterOffsetEnd": 3,
      "text": "\u0027Il",
      "basic-dependencies": [
        {
          "dep": "ROOT",
          "governor": 0,
          "governorGloss": "ROOT",
          "dependent": 1,
          "dependentGloss": "\u0027Il"
        }
      ],
      "collapsed-dependencies": [
        {
          "dep": "ROOT",
          "governor": 0,
          "governorGloss": "ROOT",
          "dependent": 1,
          "dependentGloss": "\u0027Il"
        }
      ],
      "collapsed-ccprocessed-dependencies": [
        {
          "dep": "ROOT",
          "governor": 0,
          "governorGloss": "ROOT",
          "dependent": 1,
          "dependentGloss": "\u0027Il"
        }
      ],
      "parse": "(ROOT\n  (NP (SP \u0027Il)))",
      "tokens": [
        {
          "index": 1,
          "word": "\u0027Il",
          "originalText": "\u0027Il",
          "lemma": "\u0027Il",
          "characterOffsetBegin": 0,
          "characterOffsetEnd": 3,
          "pos": "SP",
          "featuresText": "",
          "ner": "O",
          "truecase": "UPPER",
          "truecaseText": "\u0027IL",
          "isMultiwordToken": false,
          "isMultiwordFirstToken": false,
          "ud_pos": "PROPN",
          "full_morpho": "\u0027Il",
          "selected_morpho": "",
          "guessed_lemma": true
        }
      ],
...
..
..
}
  ]
}
```
  


 
