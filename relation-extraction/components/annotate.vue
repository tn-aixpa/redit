<template>
    <div>

      <div class="container">
            <h1 class='py-5'>
                Relation extraction demo
            </h1>
            <form>
<div class="mb-3">
  <label for="exampleFormControlTextarea1" class="form-label">Insert a text:</label>
  <textarea class="form-control mb-1" id="exampleFormControlTextarea1" rows="5" v-model="text"></textarea>
    <select class="form-select" aria-label="Select" @change="setSentence()" v-model="sel">
      <option selected value="">[... or select an example]</option>
      <option value="1">Il sottoscritto Luca Rosetti [...]</option>
      <option value="2">Comune di Palermo [...]</option>
      <option value="3">All'attenzione del Comune [...]</option>
      <option value="4">[Esempio normative]</option>
    </select>
</div>

<div class="row row-cols-lg-auto g-3 align-items-center">
  <div class="col-12" style="margin-left: auto;">
        <button class="btn btn-primary" @click.prevent="go" :disabled="submitDisabled">Submit</button>
    </div>
</div>
            </form>
        <div class="alert alert-danger" role="alert" v-if="error">
            {{ error }}
        </div>

        <h2 class="py-4">
            Results
        </h2>

        <div class='row'>
            <div class='col-md-6'>
                <h3>Entities</h3>

                <ul v-if="Object.keys(entities).length" class="list-group pb-3">
                    <li class="list-group-item" v-for="(entity, key) in entities">
                        <span class="badge bg-info rounded-pill">{{ entity.id }}</span>
                        <span class="badge bg-primary rounded-pill">{{ entity.type }}</span>
                        {{ entity.text }}
                    </li>
                </ul>
                <p v-else>
                    No entities found
                </p>
            </div>

            <div class='col-md-6'>
                <h3>Relations</h3>

                <ul v-if="Object.keys(relations).length" class="list-group pb-3">
                    <li class="list-group-item" v-for="(relation, key) in relations">
                        <span class="badge bg-info rounded-pill">{{ relation.id }}</span>
                        <span class="badge rounded-pill"
                            :class="{
                                'bg-danger': relation.prob > .5 && relation.prob <= .7,
                                'bg-warning': relation.prob > .7 && relation.prob <= .9,
                                'bg-success': relation.prob > .9
                            }">{{ relation.prob | round(2) }}</span>
                        {{ relation.from }}
                        <span class="badge bg-primary rounded-pill">{{ relation.type }}</span>
                        {{ relation.to }}
                    </li>
                </ul>
                <p v-else>
                    No relations found
                </p>
            </div>
        </div>

        </div>
    </div>
</template>

<script>
    module.exports = {
        data: function() {
            return {
                "text": "",
                "sel": "",
                "loading": false,
                "submitDisabled": false,
                "entities": {},
                "relations": {},
                "error": "",
                "examples": {
                    "1": "Il sottoscritto Luca Rosetti, nato a Brindisi il 4 maggio 1984 e residente a Sanremo (IM) in Via Matteotti 42 dichiara di essere titolare dell'azienda Il Matto s.n.c. con sede in Via G. Marconi n. 12.",
                    "2": "Comune di Palermo\nUfficio Anagrafe\n\nIo sottoscritto Mattia Leonardelli luogo di nascita: Chignolo Po (PV) data di nascita: 12 agosto 1991 residente in indirizzo: Via Trapani 15 città: Palermo dichiara che il proprio figlio Luca Leonardelli, nato a Chivasso il 2 settembre 2018, vorrebbe cambiare nome.",
                    "3": "All'attenzione del\nComune di Pescara\n\nIl sottoscritto Palumbo Adriano\ndata di nascita: 12 marzo 1988\nluogo di nascita: San Pietro Clarenza (CT)\n\nin qualità di titolare della ditta Multipass\ncon sede in: Via del Lupo, 12\ncittà: Vercelli\n\nchiede l'uso dello spazio pubblico per allestimento banchetto.",
                    "4": "Regolamento concernente il funzionamento del registro di artroprotesi della Provincia Autonoma di Trento (articolo 14, comma 5 bis della legge provinciale 23 luglio 2010 n. 16)\r\n\r\nNell'ambito delle finalit\u00E0 di rilevante interesse pubblico previste dal d.lgs. 30 giugno 2003 n. 196 articolo 2, comma 2, lett. v, programmazione, gestione, controllo e valutazione dell'assistenza sanitaria, e lett. cc) ricerca scientifica, il presente regolamento, ai sensi della legge provinciale 23 luglio 2010, n. 16, articolo 14, comma 5 bis, disciplina le specifiche finalit\u00E0 perseguite dal registro di artroprotesi provinciale, i tipi di dati personali trattati e le operazioni eseguibili, i soggetti che possono trattare i dati medesimi nonch\u00E9 le misure appropriate e specifiche per tutelare i diritti fondamentali e gli interessi dell'interessato.\r\n\r\nIl regolamento intende definire le modalit\u00E0 di attuazione dell'obbligo di costituire e alimentare il registro di artroprotesi, posto dall'articolo 14, comma 5 bis della legge provinciale 23 luglio 2010, n. 16."
                }
            };
        },
        props: [],
        filters: {
            "round": function(value, decimals) {
                if (!value) {
                    value = 0;
                }

                if (!decimals) {
                    decimals = 0;
                }

                value = Math.round(value * Math.pow(10, decimals)) / Math.pow(10, decimals);
                return value.toFixed(decimals);
            }
        },
        watch: {
            "loading": function(newValue) {
                if (newValue) {
                    this.submitDisabled = true;
                    this.results = [];
                    this.error = "";
                }
                else {
                    this.submitDisabled = false;
                }
            }
        },
        methods: {
            "setSentence": function() {
                if (this.sel) {
                    this.text = this.examples[this.sel];
                }
                else {
                    this.text = "";
                }
            },
            "go": function() {
                var t = this.text.trim();
                if (t.length < 10) {
                    alert("String too short");
                    return;
                }

                this.loading = true;
                var oldThis = this;

                axios.post("/re-api/tint/", t)
                .then(function (data) {
                    oldThis.entities = {};
                    oldThis.relations = {};
                    console.log(data.data);
                    var sentences = data.data.sentences;
                    for (i = 0; i < sentences.length; i++) {
                        var sentence = sentences[i];

                        var tokens = [];
                        for (j = 0; j < sentence.tokens.length; j++) {
                            var token = sentence.tokens[j];
                            var t = token.originalText;
                            if (token.isMultiwordToken && !token.isMultiwordFirstToken) {
                                t = "";
                            }
                            tokens.push(t);
                        }

                        newEntities = {};
                        for (j = 0; j < sentence.entities.length; j++) {
                            var entity = sentence.entities[j];
                            if (entity.type == "O") {
                                continue;
                            }
                            var sub_tokens = tokens.slice(entity.extentTokenSpan.start, entity.extentTokenSpan.end);
                            ent = { text: sub_tokens.join(" "), type: entity.type, id: entity.objectId };
                            newEntities[entity.objectId] = ent;
                        }
                        oldThis.entities = Object.assign({}, oldThis.entities, newEntities);

                        newRelations = {};
                        for (j = 0; j < sentence.relations.length; j++) {
                            var relation = sentence.relations[j];
                            if (relation.type == "_NR") {
                                continue;
                            }
                            var prob = relation.typeProbabilities[relation.type];
                            if (prob < 0.5) {
                                continue;
                            }
                            if (relation.args[0].type == "O" || relation.args[1].type == "O") {
                                continue;
                            }
                            rel = {
                                type: relation.type,
                                prob: prob,
                                from: newEntities[relation.args[0].objectId].text,
                                to: newEntities[relation.args[1].objectId].text,
                                id: relation.objectId
                            };
                            newRelations[relation.objectId] = rel;
                        }
                        oldThis.relations = Object.assign({}, oldThis.relations, newRelations);
                    }
                })
                .catch(function (error) {
                    oldThis.error = error;
                })
                .finally(function () {
                    oldThis.loading = false;
                });
            }
        }
    }
</script>

<style>
</style>
