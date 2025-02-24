package eu.fbk.dh.utils.wemapp.util;

import lombok.Getter;

public class Relation {
    @Getter
    private String label;
    @Getter
    private int sentenceID;

    @Getter
    private Integer mainTokenID;
    @Getter
    private Integer slaveTokenID;

    public Relation(String label, int sentenceID) {
        this.label = label;
        this.sentenceID = sentenceID;
    }

    public void set(String name, int id) {
        if (name.equals("main")) {
            mainTokenID = id;
        } else {
            slaveTokenID = id;
        }
    }

    public boolean hasBoth() {
        return mainTokenID != null && slaveTokenID != null;
    }

    @Override
    public String toString() {
        return "Relation{" +
                "label='" + label + '\'' +
                ", sentenceID=" + sentenceID +
                ", mainTokenID=" + mainTokenID +
                ", slaveTokenID=" + slaveTokenID +
                '}';
    }
}
