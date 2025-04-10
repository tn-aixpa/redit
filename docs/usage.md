# Redit

The Tint tool is packaged as a container. In order to install it, see the following instructions

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

- Once the container is up, the interface console is started by default on port 8080 and can be accessed at url
  ```
  http://localhost:8080
  ```

## Usage

### Interface
The inteface is available on url 'http://localhost:8080'

 ![image](https://github.com/tn-aixpa/redit/blob/main/assets/1.jpg)


### API

- The api is available on url
  ```
  http://localhost:8080/re-api/tint
  ```

The API can be invoked using WGET or CURL as shown below

```
..>curl -X POST http://localhost:8080/re-api/tint -H 'Content-Type: application/json' -d 'Il sottoscritto Luca Rosetti, nato a Brindisi il 4 maggio 1984 e residente a Sanremo (IM) in Via Matteotti 42 dichiara di essere titolare dell'
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
      "entities": [],
      "relations": []
    }
  ]
}cur
```
  


 
